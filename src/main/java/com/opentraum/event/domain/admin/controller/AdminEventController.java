package com.opentraum.event.domain.admin.controller;

import com.opentraum.event.domain.admin.dto.*;
import com.opentraum.event.domain.admin.service.AdminEventService;
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

    @Operation(summary = "AI 이벤트 구성 자동 생성")
    @PostMapping("/ai-generate")
    public Mono<ResponseEntity<AiGenerateResponse>> aiGenerate(@Valid @RequestBody AiGenerateRequest request) {
        return aiEventGenerateService.generate(request.getPrompt())
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 생성")
    @PostMapping
    public Mono<ResponseEntity<AdminEventResponse>> createEvent(@Valid @RequestBody AdminEventCreateRequest request) {
        return adminEventService.createEvent(request)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 목록 조회")
    @GetMapping
    public Mono<ResponseEntity<List<AdminEventResponse>>> listEvents() {
        return adminEventService.listEvents()
                .collectList()
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 상세 조회")
    @GetMapping("/{scheduleId}")
    public Mono<ResponseEntity<AdminEventResponse>> getEvent(@PathVariable Long scheduleId) {
        return adminEventService.getEvent(scheduleId)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 수정")
    @PutMapping("/{scheduleId}")
    public Mono<ResponseEntity<AdminEventResponse>> updateEvent(
            @PathVariable Long scheduleId,
            @Valid @RequestBody AdminEventCreateRequest request) {
        return adminEventService.updateEvent(scheduleId, request)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "이벤트 삭제")
    @DeleteMapping("/{scheduleId}")
    public Mono<ResponseEntity<Void>> deleteEvent(@PathVariable Long scheduleId) {
        return adminEventService.deleteEvent(scheduleId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @Operation(summary = "판매 현황 대시보드")
    @GetMapping("/{scheduleId}/dashboard")
    public Mono<ResponseEntity<AdminDashboardResponse>> getDashboard(@PathVariable Long scheduleId) {
        return adminEventService.getDashboard(scheduleId)
                .map(ResponseEntity::ok);
    }
}
