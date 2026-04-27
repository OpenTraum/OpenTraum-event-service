package com.opentraum.event.domain.outbox.repository;

import com.opentraum.event.domain.outbox.entity.OutboxEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface OutboxRepository extends ReactiveCrudRepository<OutboxEvent, Long> {
}
