package com.opentraum.event.domain.seat.service;

import com.opentraum.event.domain.concert.repository.ScheduleRepository;
import com.opentraum.event.domain.outbox.service.OutboxService;
import com.opentraum.event.domain.seat.dto.SeatKey;
import com.opentraum.event.domain.seat.entity.Seat;
import com.opentraum.event.domain.seat.repository.SeatRepository;
import com.opentraum.event.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 좌석 SAGA 배치 서비스.
 *
 * <p>reservation-service / payment-service 이벤트 Consumer가 호출한다. Redis SETNX + seats 테이블 상태 +
 * outbox_events 레코드 적재를 한 흐름으로 묶는다.
 *
 * <p>원자성: N개 중 1개라도 실패하면 이미 확보한 HOLD를 모두 되돌리고 {@code SeatHoldFailed} 이벤트를
 * 발행한다. Redis 오퍼레이션과 DB 오퍼레이션이 분산되어 있어 전체가 단일 트랜잭션이 아니지만, 실패 시
 * 보상 경로를 통해 정합성을 맞춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatSagaService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(10);
    private static final String AGGREGATE_TYPE = "event";
    private static final DateTimeFormatter HELD_UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final SeatRepository seatRepository;
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final OutboxService outboxService;
    private final ScheduleRepository scheduleRepository;

    /**
     * 배치 HOLD. Redis SETNX + DB status=HELD + Outbox SeatHeld.
     *
     * <p>실패 시 이미 잡은 HOLD 전부 해제 + SeatHoldFailed 발행.
     */
    public Mono<Void> holdBatch(Long scheduleId, List<SeatKey> seats, Long reservationId, String sagaId) {
        return holdBatch(scheduleId, seats, reservationId, sagaId, Map.of());
    }

    /**
     * 배치 HOLD - 확장 버전.
     *
     * <p>extraPayload는 SeatHeld outbox payload에 그대로 머지되어 payment-service가 별도 조회 없이
     * 결제를 진행할 수 있도록 user_id / track_type / amount 등 결제 컨텍스트를 전파한다.
     */
    public Mono<Void> holdBatch(Long scheduleId, List<SeatKey> seats, Long reservationId, String sagaId,
                                 Map<String, Object> extraPayload) {
        if (seats == null || seats.isEmpty()) {
            return Mono.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime heldUntil = now.plus(HOLD_TTL);
        List<SeatKey> acquired = new ArrayList<>();

        return Flux.fromIterable(seats)
                .concatMap(seat -> tryHoldSingle(scheduleId, seat, reservationId, now, heldUntil)
                        .flatMap(holdedSeat -> {
                            acquired.add(holdedSeat);
                            return Mono.just(holdedSeat);
                        })
                        .switchIfEmpty(Mono.defer(() -> Mono.error(new HoldConflictException(seat)))))
                .then(Mono.defer(() -> publishSeatHeld(
                        scheduleId, reservationId, sagaId, acquired, heldUntil, extraPayload)))
                .onErrorResume(HoldConflictException.class, err -> compensateHold(
                        scheduleId, reservationId, sagaId, acquired, err.failed));
    }

    /**
     * 배치 SOLD 확정. DB status=SOLD + Outbox SeatConfirmed.
     *
     * <p>SOLD 전이 실패한 좌석이 있어도 전체를 되돌리진 않는다 (결제가 이미 성공한 상태이므로 SOLD 유지가
     * 안전하다). 실패는 로그만 남기고 계속 진행한다.
     */
    public Mono<Void> confirmBatch(Long scheduleId, List<SeatKey> seats, Long reservationId, String sagaId) {
        if (seats == null || seats.isEmpty()) {
            return Mono.empty();
        }
        List<SeatKey> confirmed = new ArrayList<>();
        return Flux.fromIterable(seats)
                .concatMap(seat -> seatRepository
                        .confirmSold(scheduleId, seat.getZone(), seat.getSeatNumber(), reservationId)
                        .flatMap(rows -> {
                            if (rows > 0) {
                                confirmed.add(seat);
                            } else {
                                log.warn("confirmSold no-op: reservationId={}, zone={}, seat={}",
                                        reservationId, seat.getZone(), seat.getSeatNumber());
                            }
                            return Mono.empty();
                        }))
                .then(Mono.defer(() -> enrichSeatIds(scheduleId, confirmed)))
                .flatMap(enriched -> outboxService.publish(
                        reservationId,
                        AGGREGATE_TYPE,
                        "SeatConfirmed",
                        sagaId,
                        Map.of("schedule_id", scheduleId, "seats", toSeatJson(enriched))))
                .then();
    }

    /**
     * Lottery 당첨 좌석 배치 SOLD 확정.
     *
     * <p>HOLD 단계를 거치지 않은 AVAILABLE 좌석을 바로 SOLD 로 전이한다. DB 상태 갱신과
     * 함께 Redis 좌석 풀({@code seats:{scheduleId}:{zone}}) 에서 해당 좌석 번호를 제거해
     * 라이브 조회 경로가 이미 팔린 좌석을 AVAILABLE 로 보지 않도록 한다. 전이 실패 좌석은
     * 결제가 이미 성공한 상태이므로 전체를 되돌리지 않고 로그만 남긴다.
     */
    public Mono<Void> confirmLotteryBatch(Long scheduleId, List<SeatKey> seats, Long reservationId, String sagaId) {
        if (seats == null || seats.isEmpty()) {
            return Mono.empty();
        }
        List<SeatKey> confirmed = new ArrayList<>();
        return Flux.fromIterable(seats)
                .concatMap(seat -> seatRepository
                        .confirmSoldFromAvailable(scheduleId, seat.getZone(), seat.getSeatNumber(), reservationId)
                        .flatMap(rows -> {
                            if (rows > 0) {
                                confirmed.add(seat);
                                // 좌석 풀에서 번호 제거 (이미 SOLD 된 번호가 라이브 조회에 보이지 않도록).
                                return redisTemplate.opsForSet()
                                        .remove(RedisKeyGenerator.seatsKey(scheduleId, seat.getZone()),
                                                seat.getSeatNumber())
                                        .then();
                            }
                            log.warn("confirmLottery no-op (not AVAILABLE): reservationId={}, zone={}, seat={}",
                                    reservationId, seat.getZone(), seat.getSeatNumber());
                            return Mono.empty();
                        }))
                .then(Mono.defer(() -> enrichSeatIds(scheduleId, confirmed)))
                .flatMap(enriched -> outboxService.publish(
                        reservationId,
                        AGGREGATE_TYPE,
                        "SeatConfirmed",
                        sagaId,
                        Map.of("schedule_id", scheduleId, "seats", toSeatJson(enriched))))
                .then();
    }

    /**
     * 배치 RELEASE. DB status=AVAILABLE + Redis DEL + Outbox SeatReleased.
     */
    public Mono<Void> releaseBatch(Long scheduleId, List<SeatKey> seats, Long reservationId, String sagaId,
                                    String reason) {
        if (seats == null || seats.isEmpty()) {
            return Mono.empty();
        }
        List<SeatKey> released = new ArrayList<>();
        return Flux.fromIterable(seats)
                .concatMap(seat -> seatRepository
                        .releaseByReservation(scheduleId, seat.getZone(), seat.getSeatNumber(), reservationId)
                        .flatMap(rows -> {
                            if (rows > 0) {
                                released.add(seat);
                                return redisTemplate.delete(
                                                RedisKeyGenerator.holdKey(
                                                        scheduleId, seat.getZone(), seat.getSeatNumber()))
                                        .then();
                            }
                            log.debug("release no-op (already released): reservationId={}, zone={}, seat={}",
                                    reservationId, seat.getZone(), seat.getSeatNumber());
                            return Mono.empty();
                        }))
                .then(Mono.defer(() -> enrichSeatIds(scheduleId, released)))
                .flatMap(enriched -> outboxService.publish(
                        reservationId,
                        AGGREGATE_TYPE,
                        "SeatReleased",
                        sagaId,
                        Map.of(
                                "schedule_id", scheduleId,
                                "seats", toSeatJson(enriched),
                                "reason", reason == null ? "UNKNOWN" : reason)))
                .then();
    }

    /**
     * reservation_id 기준으로 현재 점유중인 좌석 전체를 release.
     *
     * <p>payload에 좌석 목록이 없는 보상 이벤트(ReservationCancelled 등) 처리용.
     */
    public Mono<Void> releaseAllByReservation(Long reservationId, String sagaId, String reason) {
        return seatRepository.findByReservationId(reservationId)
                .collectList()
                .flatMap(occupied -> {
                    if (occupied.isEmpty()) {
                        log.info("releaseAllByReservation: no occupied seat for reservationId={}", reservationId);
                        return Mono.empty();
                    }
                    Long scheduleId = occupied.get(0).getScheduleId();
                    List<SeatKey> keys = occupied.stream()
                            .map(s -> SeatKey.builder()
                                    .seatId(s.getId())
                                    .zone(s.getZone())
                                    .seatNumber(s.getSeatNumber())
                                    .build())
                            .toList();
                    return releaseBatch(scheduleId, keys, reservationId, sagaId, reason);
                });
    }

    /**
     * 단건 동기 HOLD. reservation-service 의 동기 REST 경로 진입점.
     *
     * <p>기존 {@link #holdBatch} 는 Kafka {@code ReservationCreated} listener 경로 전용. Live 트랙
     * critical path 에서 race condition 을 막기 위해 reservation-service 가 outbox 비동기 대신
     * 동기 REST 로 이 메서드를 호출한다.
     *
     * <p>성공 시 {@code SeatHeld} outbox 발행까지 수행한다. Kafka 로 후속 SAGA 에 전파되므로
     * payment-service 는 경로 변경 없이 기존 listener 로 결제 진행을 이어갈 수 있다.
     *
     * <p>동일 reservation 에 대해 이미 HELD 상태인 좌석이면 멱등하게 성공으로 간주하고
     * held_until 갱신 + SeatHeld 재발행은 하지 않는다 (listener 와 REST 의 동시 진입 방어).
     *
     * @return 성공 시 {@link SeatKey} (seat_id 채워진 상태), 이미 다른 예약이 점유 중이면 {@code Mono.empty()}
     */
    public Mono<SeatKey> holdSingle(Long scheduleId, String zone, String seatNumber,
                                     Long reservationId, String sagaId,
                                     Map<String, Object> extraPayload) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime heldUntil = now.plus(HOLD_TTL);
        SeatKey seat = SeatKey.builder().zone(zone).seatNumber(seatNumber).build();

        return tryHoldSingle(scheduleId, seat, reservationId, now, heldUntil)
                .flatMap(acquired -> publishSeatHeld(
                                scheduleId, reservationId, sagaId, List.of(acquired),
                                heldUntil, extraPayload)
                        .thenReturn(acquired));
    }

    // ---------- 내부 helper ----------

    private Mono<SeatKey> tryHoldSingle(Long scheduleId, SeatKey seat, Long reservationId,
                                         LocalDateTime now, LocalDateTime heldUntil) {
        String holdKey = RedisKeyGenerator.holdKey(scheduleId, seat.getZone(), seat.getSeatNumber());
        return redisTemplate.opsForValue()
                .setIfAbsent(holdKey, reservationId.toString(), HOLD_TTL)
                .flatMap(ok -> {
                    if (!Boolean.TRUE.equals(ok)) {
                        return Mono.empty();
                    }
                    return seatRepository.tryHold(
                                    scheduleId, seat.getZone(), seat.getSeatNumber(),
                                    reservationId, heldUntil, now)
                            .flatMap(rows -> {
                                if (rows > 0) {
                                    return seatRepository
                                            .findByScheduleIdAndZoneAndSeatNumber(
                                                    scheduleId, seat.getZone(), seat.getSeatNumber())
                                            .map(found -> SeatKey.builder()
                                                    .seatId(found.getId())
                                                    .zone(seat.getZone())
                                                    .seatNumber(seat.getSeatNumber())
                                                    .build());
                                }
                                // DB에는 이미 점유 -> Redis 락 되돌림
                                return redisTemplate.delete(holdKey).then(Mono.empty());
                            });
                });
    }

    private Mono<Void> publishSeatHeld(Long scheduleId, Long reservationId, String sagaId,
                                        List<SeatKey> acquired, LocalDateTime heldUntil,
                                        Map<String, Object> extraPayload) {
        Map<String, Object> payload = new HashMap<>();
        // payment-service가 별도 조회 없이 결제 가능하도록 user_id/track_type/amount 등 전파.
        if (extraPayload != null) {
            payload.putAll(extraPayload);
        }
        payload.put("schedule_id", scheduleId);
        payload.put("seats", toSeatJson(acquired));
        payload.put("held_until", HELD_UNTIL_FORMAT.format(heldUntil));
        // 멀티테넌시 전파: schedule.tenant_id를 SeatHeld payload에 자동 머지
        return scheduleRepository.findById(scheduleId)
                .flatMap(schedule -> {
                    payload.put("tenant_id", schedule.getTenantId());
                    return outboxService.publish(reservationId, AGGREGATE_TYPE, "SeatHeld", sagaId, payload);
                })
                .switchIfEmpty(outboxService.publish(reservationId, AGGREGATE_TYPE, "SeatHeld", sagaId, payload).then(Mono.empty()))
                .then();
    }

    private Mono<Void> compensateHold(Long scheduleId, Long reservationId, String sagaId,
                                       List<SeatKey> acquired, SeatKey failedSeat) {
        log.warn("holdBatch conflict: reservationId={}, failed zone={}/{}, acquiredBeforeRollback={}",
                reservationId, failedSeat.getZone(), failedSeat.getSeatNumber(), acquired.size());
        return Flux.fromIterable(acquired)
                .concatMap(seat -> seatRepository
                        .releaseByReservation(scheduleId, seat.getZone(), seat.getSeatNumber(), reservationId)
                        .then(redisTemplate.delete(
                                RedisKeyGenerator.holdKey(scheduleId, seat.getZone(), seat.getSeatNumber())))
                        .then())
                .then(outboxService.publish(
                        reservationId,
                        AGGREGATE_TYPE,
                        "SeatHoldFailed",
                        sagaId,
                        Map.of(
                                "schedule_id", scheduleId,
                                "failed_seats", List.of(Map.of(
                                        "zone", failedSeat.getZone(),
                                        "seat_number", failedSeat.getSeatNumber())),
                                "reason", "ALREADY_HELD")))
                .then();
    }

    private Mono<List<SeatKey>> enrichSeatIds(Long scheduleId, List<SeatKey> seats) {
        if (seats.isEmpty()) {
            return Mono.just(seats);
        }
        return Flux.fromIterable(seats)
                .concatMap(seat -> {
                    if (seat.getSeatId() != null) {
                        return Mono.just(seat);
                    }
                    return seatRepository
                            .findByScheduleIdAndZoneAndSeatNumber(
                                    scheduleId, seat.getZone(), seat.getSeatNumber())
                            .map(found -> {
                                seat.setSeatId(found.getId());
                                return seat;
                            })
                            .defaultIfEmpty(seat);
                })
                .collectList();
    }

    private List<Map<String, Object>> toSeatJson(List<SeatKey> seats) {
        List<Map<String, Object>> result = new ArrayList<>(seats.size());
        for (SeatKey key : seats) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("seat_id", key.getSeatId());
            entry.put("zone", key.getZone());
            entry.put("seat_number", key.getSeatNumber());
            result.add(entry);
        }
        return result;
    }

    /**
     * HOLD 만료 보정 (조회 시).
     *
     * <p>status='HELD' AND held_until < now 면 AVAILABLE + held_until=NULL + reservation_id=NULL 로
     * 되돌려 읽기 측에서 본 상태가 사실상의 상태와 일치하게 한다.
     */
    public Mono<Seat> reconcileExpiredHold(Seat seat) {
        if (seat == null || !"HELD".equals(seat.getStatus())) {
            return Mono.justOrEmpty(seat);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = seat.getHeldUntil();
        if (until != null && until.isAfter(now)) {
            return Mono.just(seat);
        }
        return seatRepository.expireHoldIfStale(seat.getId(), now)
                .doOnNext(rows -> {
                    if (rows > 0) {
                        log.debug("stale HELD auto-expired on read: seatId={}", seat.getId());
                    }
                })
                .thenReturn(seat)
                .map(s -> {
                    s.setStatus("AVAILABLE");
                    s.setHeldUntil(null);
                    s.setReservationId(null);
                    return s;
                });
    }

    private static final class HoldConflictException extends RuntimeException {
        private final SeatKey failed;

        HoldConflictException(SeatKey failed) {
            super("hold conflict on " + failed.getZone() + "/" + failed.getSeatNumber());
            this.failed = failed;
        }
    }
}
