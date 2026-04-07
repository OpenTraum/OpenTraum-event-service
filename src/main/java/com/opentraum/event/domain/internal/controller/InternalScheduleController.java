package com.opentraum.event.domain.internal.controller;

import com.opentraum.event.domain.concert.entity.Schedule;
import com.opentraum.event.domain.concert.repository.GradeRepository;
import com.opentraum.event.domain.concert.repository.ScheduleRepository;
import com.opentraum.event.domain.concert.service.ScheduleService;
import com.opentraum.event.domain.internal.dto.ScheduleInfo;
import com.opentraum.event.global.exception.BusinessException;
import com.opentraum.event.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * reservation-service 등 내부 서비스 간 통신용 API.
 * Gateway를 거치지 않고 서비스 간 직접 호출합니다.
 */
@Tag(name = "Internal - Schedule", description = "내부 서비스 간 통신용 스케줄 API")
@RestController
@RequestMapping("/api/v1/internal/schedules")
@RequiredArgsConstructor
public class InternalScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleRepository scheduleRepository;
    private final GradeRepository gradeRepository;

    @Operation(summary = "스케줄 단건 조회")
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ScheduleInfo>> getSchedule(@PathVariable Long id) {
        return scheduleService.findScheduleOrThrow(id)
                .map(ScheduleInfo::from)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "조건별 스케줄 목록 조회", description = "ticketOpenAt <= threshold AND status != excludeStatus")
    @GetMapping
    public Flux<ScheduleInfo> getSchedules(
            @RequestParam String ticketOpenBefore,
            @RequestParam String excludeStatus) {
        LocalDateTime threshold = LocalDateTime.parse(ticketOpenBefore);
        return scheduleRepository.findByTicketOpenAtLessThanEqualAndStatusNot(threshold, excludeStatus)
                .map(ScheduleInfo::from);
    }

    @Operation(summary = "스케줄 상태 변경")
    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<Void>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return scheduleRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                .flatMap(schedule -> {
                    schedule.setStatus(status);
                    return scheduleRepository.save(schedule);
                })
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @Operation(summary = "등급별 구역 목록")
    @GetMapping("/{id}/grades/{grade}/zones")
    public Mono<ResponseEntity<List<String>>> getZonesByGrade(
            @PathVariable Long id,
            @PathVariable String grade) {
        return scheduleService.getZonesByGrade(id, grade)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "등급-구역 유효성 검증")
    @GetMapping("/{id}/grades/{grade}/zones/{zone}/validate")
    public Mono<ResponseEntity<Void>> validateGradeAndZone(
            @PathVariable Long id,
            @PathVariable String grade,
            @PathVariable String zone) {
        return scheduleService.validateGradeAndZone(id, grade, zone)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @Operation(summary = "등급명 목록 조회")
    @GetMapping("/{id}/grades")
    public Flux<String> getGrades(@PathVariable Long id) {
        return gradeRepository.findByScheduleId(id)
                .map(grade -> grade.getGrade());
    }
}
