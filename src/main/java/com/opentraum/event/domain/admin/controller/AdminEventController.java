package com.opentraum.event.domain.admin.controller;

import com.opentraum.event.domain.admin.dto.*;
import com.opentraum.event.domain.admin.service.AdminEventService;
import com.opentraum.event.domain.admin.service.AdminInsightService;
import com.opentraum.event.domain.admin.service.AiEventGenerateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Admin - Events", description = "어드민 이벤트 관리 API")
@RestController
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final AiEventGenerateService aiEventGenerateService;
    private final AdminEventService adminEventService;
    private final AdminInsightService adminInsightService;

    @Operation(summary = "AI 이벤트 구성 자동 생성")
    @PostMapping("/ai-generate")
    public Mono<ResponseEntity<AiGenerateResponse>> aiGenerate(@Valid @RequestBody AiGenerateRequest request) {
        return aiEventGenerateService.generate(request.getPrompt())
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 생성")
    @PostMapping
    public Mono<ResponseEntity<AdminEventResponse>> createEvent(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @Valid @RequestBody AdminEventCreateRequest request) {
        return adminEventService.createEvent(tenantId, request)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 목록 조회")
    @GetMapping
    public Mono<ResponseEntity<List<AdminEventResponse>>> listEvents(
            @RequestHeader("X-Tenant-Id") String tenantId) {
        return adminEventService.listEvents(tenantId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 상세 조회")
    @GetMapping("/{scheduleId}")
    public Mono<ResponseEntity<AdminEventResponse>> getEvent(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long scheduleId) {
        return adminEventService.getEvent(tenantId, scheduleId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 수정")
    @PutMapping("/{scheduleId}")
    public Mono<ResponseEntity<AdminEventResponse>> updateEvent(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long scheduleId,
            @Valid @RequestBody AdminEventCreateRequest request) {
        return adminEventService.updateEvent(tenantId, scheduleId, request)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 삭제")
    @DeleteMapping("/{scheduleId}")
    public Mono<ResponseEntity<Void>> deleteEvent(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long scheduleId) {
        return adminEventService.deleteEvent(tenantId, scheduleId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @Operation(summary = "판매 현황 대시보드")
    @GetMapping("/{scheduleId}/dashboard")
    public Mono<ResponseEntity<AdminDashboardResponse>> getDashboard(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long scheduleId) {
        return adminEventService.getDashboard(tenantId, scheduleId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "AI 운영 인사이트", description = "판매 현황을 분석하여 위험도, 인사이트, 추천 액션을 제공합니다")
    @GetMapping("/{scheduleId}/insights")
    public Mono<ResponseEntity<AdminInsightResponse>> getInsights(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable Long scheduleId) {
        return adminInsightService.generateInsights(tenantId, scheduleId)
                .map(ResponseEntity::ok);
    }
}
