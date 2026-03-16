package com.opentraum.event.domain.repository;

import com.opentraum.event.domain.entity.Event;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface EventRepository extends ReactiveCrudRepository<Event, Long> {

    Flux<Event> findByTenantId(String tenantId);

    Flux<Event> findByStatus(String status);

    @Query("SELECT * FROM events WHERE tenant_id = :tenantId AND title ILIKE '%' || :keyword || '%'")
    Flux<Event> searchByTenantIdAndTitle(String tenantId, String keyword);

    Flux<Event> findByTenantIdAndStatus(String tenantId, String status);
}
