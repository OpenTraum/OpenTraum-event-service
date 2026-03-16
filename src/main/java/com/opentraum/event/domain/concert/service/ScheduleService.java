package com.opentraum.event.domain.concert.service;

import com.opentraum.event.domain.concert.dto.GradeResponse;
import com.opentraum.event.domain.concert.entity.Schedule;
import com.opentraum.event.domain.concert.repository.GradeRepository;
import com.opentraum.event.domain.concert.repository.ScheduleRepository;
import com.opentraum.event.domain.concert.repository.ZoneRepository;
import com.opentraum.event.global.exception.BusinessException;
import com.opentraum.event.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final GradeRepository gradeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ZoneRepository zoneRepository;

    public Mono<Schedule> findScheduleOrThrow(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)));
    }

    public Flux<GradeResponse> getGradesByScheduleId(Long scheduleId) {
        return gradeRepository.findByScheduleId(scheduleId)
                .map(g -> GradeResponse.builder()
                        .grade(g.getGrade())
                        .price(g.getPrice())
                        .build());
    }

    public Mono<List<String>> getZonesByGrade(Long scheduleId, String grade) {
        return zoneRepository.findByScheduleIdAndGrade(scheduleId, grade)
                .map(zone -> zone.getZone())
                .collectList();
    }

    public Mono<Void> validateGradeAndZone(Long scheduleId, String grade, String zone) {
        return zoneRepository.findByScheduleIdAndGradeAndZone(scheduleId, grade, zone)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.INVALID_GRADE_ZONE)))
                .then();
    }

    public Flux<String> getZoneNamesByScheduleId(Long scheduleId) {
        return zoneRepository.findByScheduleId(scheduleId).map(zone -> zone.getZone());
    }
}
