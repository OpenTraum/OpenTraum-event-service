package com.opentraum.event.domain.concert.repository;

import com.opentraum.event.domain.concert.entity.Concert;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ConcertRepository extends ReactiveCrudRepository<Concert, Long> {

    Flux<Concert> findByTenantId(String tenantId);
}
