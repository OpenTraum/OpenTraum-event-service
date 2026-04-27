package com.opentraum.event.domain.outbox.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Consumer 멱등성 테이블.
 *
 * <p>Kafka Consumer가 이벤트를 처리하기 전 {@code event_id}로 조회해 중복 처리를 막는다.
 * PK 자체가 {@code event_id}이므로 insert 충돌로 exactly-once 경계를 형성한다.
 *
 * <p><b>{@link Persistable} 구현 이유</b>: Spring Data R2DBC 의 {@code save()} 는 기본적으로
 * {@code @Id} 가 non-null 이면 UPDATE 로 동작. 본 엔티티는 {@code eventId} 를 호출자가 채워서
 * save 하므로 Persistable 없이는 affected=0 UPDATE 만 실행되고 INSERT 가 누락된다.
 * (payment-service 의 같은 엔티티 참조)
 */
@Table("processed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent implements Persistable<String> {

    @Id
    @Column("event_id")
    private String eventId;

    @Column("consumer_group")
    private String consumerGroup;

    @Column("processed_at")
    private LocalDateTime processedAt;

    @Transient
    @Builder.Default
    private boolean newEntity = true;

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
