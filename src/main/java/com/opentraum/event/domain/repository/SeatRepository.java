package com.opentraum.event.domain.repository;

import com.opentraum.event.domain.entity.Seat;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SeatRepository extends ReactiveCrudRepository<Seat, Long> {

    Flux<Seat> findByEventId(Long eventId);

    Flux<Seat> findByEventIdAndStatus(Long eventId, String status);

    Flux<Seat> findByEventIdAndGrade(Long eventId, String grade);
}
