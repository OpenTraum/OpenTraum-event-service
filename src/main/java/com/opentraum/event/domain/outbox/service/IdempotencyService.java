package com.opentraum.event.domain.outbox.service;

import com.opentraum.event.domain.outbox.entity.ProcessedEvent;
import com.opentraum.event.domain.outbox.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Kafka Consumer 멱등성 서비스.
 *
 * <p>{@code processed_events} 테이블의 PK(event_id) 충돌을 통해 exactly-once 경계를 형성한다.
 * 같은 event_id를 두 번 markProcessed 하려 하면 {@link DuplicateKeyException}을 빈 완료 시그널로
 * 흘려보내 멱등하게 처리한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * 이벤트가 이미 처리되었는지 확인한다.
     *
     * @param eventId       Kafka 헤더 또는 outbox_events.event_id
     * @param consumerGroup 같은 event_id라도 consumer_group이 다르면 별도 처리로 간주해야 하므로
     *                      현재 구현은 PK만 검사한다. (consumer_group 컬럼은 감사용.)
     */
    public Mono<Boolean> isProcessed(String eventId, String consumerGroup) {
        return processedEventRepository.existsByEventId(eventId);
    }

    /**
     * 이벤트를 처리 완료로 표시한다.
     *
     * <p>동일 event_id가 동시에 들어와도 PK 충돌은 {@link DuplicateKeyException}으로 나타나며,
     * 이는 "이미 누가 처리했다" 와 동일하므로 빈 Mono로 흡수한다.
     */
    public Mono<Void> markProcessed(String eventId, String consumerGroup) {
        ProcessedEvent record = ProcessedEvent.builder()
                .eventId(eventId)
                .consumerGroup(consumerGroup)
                .processedAt(LocalDateTime.now())
                .build();

        return processedEventRepository.save(record)
                .doOnSuccess(saved -> log.debug(
                        "event marked processed: eventId={}, consumerGroup={}", eventId, consumerGroup))
                .onErrorResume(DuplicateKeyException.class, e -> {
                    log.debug("event already processed (duplicate key): eventId={}", eventId);
                    return Mono.empty();
                })
                .then();
    }
}
