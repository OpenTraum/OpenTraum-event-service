package com.opentraum.event.domain.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opentraum.event.domain.outbox.service.IdempotencyService;
import com.opentraum.event.domain.seat.dto.SeatKey;
import com.opentraum.event.domain.seat.service.SeatSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * reservation-service Outbox -> Kafka 이벤트 Consumer.
 *
 * <p>event_type 헤더 기준으로 분기. 모든 분기는 IdempotencyService로 중복 처리 차단 후 SeatSagaService의
 * 배치 메서드를 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventListener {

    private static final String CONSUMER_GROUP = "event-saga-group";
    private static final String EVENT_ID_HEADER = "event_id";
    private static final String EVENT_TYPE_HEADER = "event_type";
    private static final String SAGA_ID_HEADER = "saga_id";

    private final SeatSagaService seatSagaService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "opentraum.reservation", groupId = CONSUMER_GROUP)
    public void onReservationEvent(ConsumerRecord<String, String> record) {
        String eventId = header(record, EVENT_ID_HEADER);
        String eventType = header(record, EVENT_TYPE_HEADER);
        String sagaId = header(record, SAGA_ID_HEADER);

        if (eventId == null || eventType == null) {
            log.warn("reservation event missing headers: eventId={}, eventType={}", eventId, eventType);
            return;
        }

        idempotencyService.isProcessed(eventId, CONSUMER_GROUP)
                .flatMap(processed -> {
                    if (Boolean.TRUE.equals(processed)) {
                        log.debug("duplicate reservation event skipped: eventId={}", eventId);
                        return Mono.empty();
                    }
                    return dispatch(eventType, sagaId, record.value())
                            .then(idempotencyService.markProcessed(eventId, CONSUMER_GROUP));
                })
                .doOnError(err -> log.error(
                        "reservation event handling failed: eventId={}, eventType={}, err={}",
                        eventId, eventType, err.getMessage(), err))
                .block();
    }

    private Mono<Void> dispatch(String eventType, String sagaId, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String effectiveSagaId = sagaId != null ? sagaId : textOrNull(root, "saga_id");
            Long reservationId = root.hasNonNull("reservation_id") ? root.get("reservation_id").asLong() : null;

            if (reservationId == null) {
                log.warn("reservation event missing reservation_id: type={}", eventType);
                return Mono.empty();
            }

            switch (eventType) {
                case "ReservationCreated" -> {
                    // Live 트랙 좌석 HOLD 는 reservation-service 가 동기 REST
                    // POST /api/v1/internal/seats/hold 로 처리한다 (race condition 방지).
                    // 이 listener 는 중복 진입 방지를 위해 무시만 한다.
                    log.debug("ReservationCreated ignored (hold handled via sync REST): reservationId={}", reservationId);
                    return Mono.empty();
                }
                case "LotteryReservationCreated" -> {
                    log.debug("LotteryReservationCreated skipped (no hold): reservationId={}", reservationId);
                    return Mono.empty();
                }
                case "LotterySeatAssigned" -> {
                    // reservation-service가 Fisher-Yates로 좌석 배정을 끝낸 후 발행하는 이벤트.
                    // payload.seats 를 AVAILABLE → SOLD 로 확정하고 Redis 좌석 풀에서 제거한다.
                    Long scheduleId = root.hasNonNull("schedule_id") ? root.get("schedule_id").asLong() : null;
                    List<SeatKey> seats = parseSeats(root.get("seats"));
                    if (scheduleId == null || seats.isEmpty()) {
                        log.warn("LotterySeatAssigned missing scheduleId/seats: reservationId={}", reservationId);
                        return Mono.empty();
                    }
                    return seatSagaService.confirmLotteryBatch(scheduleId, seats, reservationId, effectiveSagaId);
                }
                case "ReservationCancelled", "ReservationRefundRequested" -> {
                    String reason = "ReservationCancelled".equals(eventType)
                            ? firstNonNull(textOrNull(root, "reason"), "USER_CANCELLED")
                            : "USER_CANCELLED";
                    // payload에 seats가 실려있으면 그것 우선, 없으면 reservation_id 기준 전체 release
                    List<SeatKey> seats = parseSeats(root.get("seats"));
                    if (seats.isEmpty()) {
                        return seatSagaService.releaseAllByReservation(reservationId, effectiveSagaId, reason);
                    }
                    Long scheduleId = root.hasNonNull("schedule_id") ? root.get("schedule_id").asLong() : null;
                    if (scheduleId == null) {
                        return seatSagaService.releaseAllByReservation(reservationId, effectiveSagaId, reason);
                    }
                    return seatSagaService.releaseBatch(scheduleId, seats, reservationId, effectiveSagaId, reason);
                }
                default -> {
                    log.debug("unhandled reservation event_type={}", eventType);
                    return Mono.empty();
                }
            }
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private List<SeatKey> parseSeats(JsonNode seatsNode) {
        List<SeatKey> out = new ArrayList<>();
        if (seatsNode == null || !seatsNode.isArray()) {
            return out;
        }
        for (JsonNode n : seatsNode) {
            SeatKey key = SeatKey.builder()
                    .seatId(n.hasNonNull("seat_id") ? n.get("seat_id").asLong() : null)
                    .zone(textOrNull(n, "zone"))
                    .seatNumber(textOrNull(n, "seat_number"))
                    .build();
            if (key.getZone() != null && key.getSeatNumber() != null) {
                out.add(key);
            }
        }
        return out;
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
