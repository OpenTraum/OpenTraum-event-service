package com.opentraum.event.domain.concert.repository;

import com.opentraum.event.domain.concert.entity.Zone;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ZoneRepository extends ReactiveCrudRepository<Zone, Long> {

    Flux<Zone> findByScheduleId(Long scheduleId);

    Flux<Zone> findByScheduleIdAndGrade(Long scheduleId, String grade);

    Mono<Zone> findByScheduleIdAndZone(Long scheduleId, String zone);

    Mono<Zone> findByScheduleIdAndGradeAndZone(Long scheduleId, String grade, String zone);
}
