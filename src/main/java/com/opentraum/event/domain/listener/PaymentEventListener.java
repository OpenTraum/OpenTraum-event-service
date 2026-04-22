package com.opentraum.event.domain.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opentraum.event.domain.outbox.service.IdempotencyService;
import com.opentraum.event.domain.seat.dto.SeatKey;
import com.opentraum.event.domain.seat.entity.Seat;
import com.opentraum.event.domain.seat.repository.SeatRepository;
import com.opentraum.event.domain.seat.service.SeatSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * payment-service Outbox -> Kafka 이벤트 Consumer.
 *
 * <ul>
 *     <li>PaymentCompleted (LIVE)  : reservation_id 기준 HELD 좌석 -> SOLD 확정</li>
 *     <li>PaymentCompleted (LOTTERY): 실제 배정은 별도 스케줄러가 수행. 본 Listener는 skip.</li>
 *     <li>PaymentFailed            : reservation_id 기준 HELD 좌석 전부 release (보상).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private static final String CONSUMER_GROUP = "event-saga-group";
    private static final String EVENT_ID_HEADER = "event_id";
    private static final String EVENT_TYPE_HEADER = "event_type";
    private static final String SAGA_ID_HEADER = "saga_id";

    private final SeatSagaService seatSagaService;
    private final SeatRepository seatRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "opentraum.payment", groupId = CONSUMER_GROUP)
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        String eventId = header(record, EVENT_ID_HEADER);
        String eventType = header(record, EVENT_TYPE_HEADER);
        String sagaId = header(record, SAGA_ID_HEADER);

        if (eventId == null || eventType == null) {
            log.warn("payment event missing headers: eventId={}, eventType={}", eventId, eventType);
            return;
        }

        idempotencyService.isProcessed(eventId, CONSUMER_GROUP)
                .flatMap(processed -> {
                    if (Boolean.TRUE.equals(processed)) {
                        log.debug("duplicate payment event skipped: eventId={}", eventId);
                        return Mono.empty();
                    }
                    return dispatch(eventType, sagaId, record.value())
                            .then(idempotencyService.markProcessed(eventId, CONSUMER_GROUP));
                })
                .doOnError(err -> log.error(
                        "payment event handling failed: eventId={}, eventType={}, err={}",
                        eventId, eventType, err.getMessage(), err))
                .block();
    }

    private Mono<Void> dispatch(String eventType, String sagaId, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String effectiveSagaId = sagaId != null ? sagaId : textOrNull(root, "saga_id");
            Long reservationId = root.hasNonNull("reservation_id") ? root.get("reservation_id").asLong() : null;

            if (reservationId == null) {
                log.warn("payment event missing reservation_id: type={}", eventType);
                return Mono.empty();
            }

            switch (eventType) {
                case "PaymentCompleted" -> {
                    String trackType = textOrNull(root, "track_type");
                    if ("LOTTERY".equalsIgnoreCase(trackType)) {
                        log.debug("PaymentCompleted LOTTERY ignored (scheduler will assign): reservationId={}",
                                reservationId);
                        return Mono.empty();
                    }
                    // LIVE -> reservation 점유 좌석을 모두 SOLD 확정
                    return seatRepository.findByReservationId(reservationId)
                            .collectList()
                            .flatMap(seats -> {
                                if (seats.isEmpty()) {
                                    log.warn("PaymentCompleted but no held seat: reservationId={}", reservationId);
                                    return Mono.empty();
                                }
                                Long scheduleId = seats.get(0).getScheduleId();
                                List<SeatKey> keys = seats.stream()
                                        .map(this::toKey)
                                        .toList();
                                return seatSagaService.confirmBatch(
                                        scheduleId, keys, reservationId, effectiveSagaId);
                            });
                }
                case "PaymentFailed" -> {
                    String reason = firstNonNull(textOrNull(root, "reason"), "PAYMENT_FAILED");
                    return seatSagaService.releaseAllByReservation(reservationId, effectiveSagaId, reason);
                }
                case "RefundCompleted" -> {
                    // 환불 완료 -> 좌석도 release (사용자 취소 SAGA)
                    return seatSagaService.releaseAllByReservation(
                            reservationId, effectiveSagaId, "USER_CANCELLED");
                }
                default -> {
                    log.debug("unhandled payment event_type={}", eventType);
                    return Mono.empty();
                }
            }
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private SeatKey toKey(Seat s) {
        return SeatKey.builder()
                .seatId(s.getId())
                .zone(s.getZone())
                .seatNumber(s.getSeatNumber())
                .build();
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
