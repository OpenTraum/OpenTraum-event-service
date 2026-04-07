package com.opentraum.event.domain.seat.service;

import com.opentraum.event.domain.concert.entity.Zone;
import com.opentraum.event.domain.concert.repository.ZoneRepository;
import com.opentraum.event.domain.seat.dto.ZoneSeatAssignmentResponse;
import com.opentraum.event.domain.seat.entity.Seat;
import com.opentraum.event.domain.seat.repository.SeatRepository;
import com.opentraum.event.global.util.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatPoolService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;

    /**
     * seats 테이블 기준으로 해당 회차 좌석 풀 초기화.
     * 추가: 등급별 재고 카운터(stock:{scheduleId}:{grade})도 함께 초기화.
     */
    public Mono<Void> initializeSeatPools(Long scheduleId) {
        return seatRepository.findByScheduleId(scheduleId)
                .collectMultimap(Seat::getZone, Seat::getSeatNumber)
                .flatMap(zoneToNumbers -> Flux.fromIterable(zoneToNumbers.entrySet())
                        .flatMap(entry -> initializeSeatPoolWithNumbers(
                                scheduleId,
                                entry.getKey(),
                                new ArrayList<>(entry.getValue())))
                        .then())
                .then(initializeStockCounters(scheduleId))
                .doOnSuccess(v -> log.info("좌석 풀 + 재고 카운터 초기화 완료: scheduleId={}", scheduleId));
    }

    private Mono<Void> initializeStockCounters(Long scheduleId) {
        return seatRepository.findSeatCountByScheduleIdGroupByGrade(scheduleId)
                .flatMap(gradeSeatCount -> {
                    String stockKey = RedisKeyGenerator.stockKey(scheduleId, gradeSeatCount.getGrade());
                    String countValue = String.valueOf(gradeSeatCount.getCount());
                    return redisTemplate.opsForValue().set(stockKey, countValue)
                            .doOnSuccess(v -> log.info("재고 카운터 초기화: scheduleId={}, grade={}, stock={}",
                                    scheduleId, gradeSeatCount.getGrade(), countValue));
                })
                .then();
    }

    public Mono<Void> initializeSeatPoolWithNumbers(Long scheduleId, String zone, List<String> seatNumbers) {
        if (seatNumbers.isEmpty()) {
            return Mono.empty();
        }
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.delete(poolKey)
                .then(redisTemplate.opsForSet()
                        .add(poolKey, seatNumbers.toArray(new String[0])))
                .doOnSuccess(c -> log.info("좌석 풀 초기화: scheduleId={}, zone={}, count={}",
                        scheduleId, zone, seatNumbers.size()))
                .then();
    }

    public Mono<Long> getRemainingSeats(Long scheduleId, String zone) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.opsForSet().size(poolKey);
    }

    public Mono<Long> getRemainingSeatsTotal(Long scheduleId) {
        return zoneRepository.findByScheduleId(scheduleId)
                .map(Zone::getZone)
                .collectList()
                .flatMap(zones -> {
                    if (zones.isEmpty()) {
                        return Mono.just(0L);
                    }
                    return redisTemplate.execute(connection ->
                            Flux.fromIterable(zones)
                                    .map(z -> ByteBuffer.wrap(RedisKeyGenerator.seatsKey(scheduleId, z)
                                            .getBytes(StandardCharsets.UTF_8)))
                                    .flatMap(key -> connection.setCommands().sCard(key))
                                    .reduce(0L, Long::sum)
                    ).next();
                });
    }

    public Flux<String> getAvailableSeats(Long scheduleId, String zone) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.opsForSet().members(poolKey);
    }

    public Mono<Boolean> selectSeat(Long scheduleId, String zone, String seatNumber) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.opsForSet()
                .remove(poolKey, seatNumber)
                .map(removed -> removed > 0)
                .doOnSuccess(success -> log.info("좌석 선택: scheduleId={}, zone={}, seat={}, success={}",
                        scheduleId, zone, seatNumber, success));
    }

    public Mono<Boolean> returnSeat(Long scheduleId, String zone, String seatNumber) {
        String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);
        return redisTemplate.opsForSet()
                .add(poolKey, seatNumber)
                .map(added -> added > 0)
                .doOnSuccess(success -> log.info("좌석 반환: scheduleId={}, zone={}, seat={}",
                        scheduleId, zone, seatNumber));
    }

    public Mono<ZoneSeatAssignmentResponse> popRandomSeat(Long scheduleId, String grade) {
        return zoneRepository.findByScheduleIdAndGrade(scheduleId, grade)
                .collectList()
                .flatMap(zones -> {
                    if (zones.isEmpty()) {
                        return Mono.empty();
                    }
                    int idx = ThreadLocalRandom.current().nextInt(zones.size());
                    String zone = zones.get(idx).getZone();
                    String poolKey = RedisKeyGenerator.seatsKey(scheduleId, zone);

                    return redisTemplate.opsForSet().pop(poolKey)
                            .map(seatNumber -> {
                                log.info("좌석 추출: scheduleId={}, grade={}, zone={}, seat={}",
                                        scheduleId, grade, zone, seatNumber);
                                return new ZoneSeatAssignmentResponse(zone, seatNumber);
                            });
                });
    }

    public Mono<List<ZoneSeatAssignmentResponse>> getAvailableSeatsForGrade(Long scheduleId, String grade) {
        return zoneRepository.findByScheduleIdAndGrade(scheduleId, grade)
                .flatMap(zone -> getAvailableSeats(scheduleId, zone.getZone())
                        .map(seatNumber -> new ZoneSeatAssignmentResponse(zone.getZone(), seatNumber)))
                .collectList();
    }

    public Mono<Long> getRemainingSeatsLottery(Long scheduleId, String grade) {
        return seatRepository.countByScheduleIdAndGrade(scheduleId, grade)
                .map(total -> total / 2)
                .defaultIfEmpty(0L);
    }

    public Mono<Long> getRemainingSeatsForGrade(Long scheduleId, String grade) {
        return zoneRepository.findByScheduleIdAndGrade(scheduleId, grade)
                .flatMap(zone -> redisTemplate.opsForSet()
                        .size(RedisKeyGenerator.seatsKey(scheduleId, zone.getZone())))
                .reduce(0L, Long::sum);
    }
}
