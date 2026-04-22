package com.opentraum.event.domain.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opentraum.event.domain.outbox.entity.OutboxEvent;
import com.opentraum.event.domain.outbox.repository.OutboxRepository;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SAGA Outbox 발행 서비스.
 *
 * <p>비즈니스 트랜잭션과 동일 DB 트랜잭션 안에서 호출되어 {@code outbox_events}에 레코드를 적재한다.
 * Debezium이 해당 테이블의 binlog를 감시해 Kafka 토픽(aggregate_type 기준 라우팅)으로 발행한다.
 *
 * <p>payload는 호출자가 넘긴 필드 위에 공통 필드(saga_id, reservation_id, occurred_at)를
 * 자동 주입한다. 이벤트 스키마 합의문(/tmp/outbox-opentraum/EVENT-SCHEMA.md) 참조.
 */
@Service
@Slf4j
public class OutboxService {

    private static final DateTimeFormatter OCCURRED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public OutboxService(OutboxRepository outboxRepository,
                         ObjectMapper objectMapper,
                         @Autowired(required = false) Tracer tracer) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    /**
     * Outbox 레코드를 적재한다.
     *
     * @param aggregateId   집계 루트 ID (event-service에서는 reservation_id를 그대로 사용)
     * @param aggregateType Debezium EventRouter가 사용할 라우팅 키 (event-service는 "event")
     * @param eventType     이벤트 타입 (e.g. SeatHeld, SeatConfirmed, SeatReleased)
     * @param sagaId        SAGA 상관관계 UUID
     * @param payload       이벤트별 고유 필드. 공통 필드는 자동 주입된다.
     */
    public Mono<OutboxEvent> publish(
            Long aggregateId,
            String aggregateType,
            String eventType,
            String sagaId,
            Map<String, Object> payload) {

        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> enriched = new HashMap<>();
        if (payload != null) {
            enriched.putAll(payload);
        }
        enriched.put("saga_id", sagaId);
        enriched.put("reservation_id", aggregateId);
        enriched.put("occurred_at", OCCURRED_AT_FORMAT.format(now));

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(enriched);
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException(
                    "Failed to serialize outbox payload for eventType=" + eventType, e));
        }

        String traceId = currentTraceId();

        OutboxEvent event = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .sagaId(sagaId)
                .payload(payloadJson)
                .traceId(traceId)
                .occurredAt(now)
                .build();

        return outboxRepository.save(event)
                .doOnSuccess(saved -> log.debug(
                        "outbox published: eventType={}, eventId={}, sagaId={}, aggregateId={}, traceId={}",
                        saved.getEventType(), saved.getEventId(), saved.getSagaId(),
                        saved.getAggregateId(), saved.getTraceId()));
    }

    private String currentTraceId() {
        if (tracer == null) return null;
        var span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }
}
