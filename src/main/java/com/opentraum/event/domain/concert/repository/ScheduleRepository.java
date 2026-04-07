package com.opentraum.event.domain.concert.repository;

import com.opentraum.event.domain.concert.entity.Schedule;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ScheduleRepository extends ReactiveCrudRepository<Schedule, Long> {

    Flux<Schedule> findByConcertId(Long concertId);

    Flux<Schedule> findByTicketOpenAtLessThanEqual(LocalDateTime time);

    Flux<Schedule> findByTicketOpenAtLessThanEqualAndStatusNot(LocalDateTime time, String status);

    Mono<Void> deleteByConcertId(Long concertId);
}
