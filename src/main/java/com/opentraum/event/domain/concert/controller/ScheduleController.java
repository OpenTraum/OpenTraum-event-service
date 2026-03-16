package com.opentraum.event.domain.concert.controller;

import com.opentraum.event.domain.concert.dto.GradeResponse;
import com.opentraum.event.domain.concert.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Schedule", description = "회차별 등급/구역 조회 API")
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "등급 목록 조회")
    @GetMapping("/{scheduleId}/grades")
    public Mono<ResponseEntity<List<GradeResponse>>> getGrades(
            @PathVariable Long scheduleId) {
        return scheduleService.getGradesByScheduleId(scheduleId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "등급별 구역 목록 조회")
    @GetMapping("/{scheduleId}/grades/{grade}/zones")
    public Mono<ResponseEntity<List<String>>> getZonesByGrade(
            @PathVariable Long scheduleId,
            @PathVariable String grade) {
        return scheduleService.getZonesByGrade(scheduleId, grade)
                .map(ResponseEntity::ok);
    }
}
