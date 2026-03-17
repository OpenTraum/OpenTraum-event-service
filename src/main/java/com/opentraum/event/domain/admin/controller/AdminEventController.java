package com.opentraum.event.domain.admin.controller;

import com.opentraum.event.domain.admin.dto.AiGenerateRequest;
import com.opentraum.event.domain.admin.dto.AiGenerateResponse;
import com.opentraum.event.domain.admin.service.AiEventGenerateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Admin - Events", description = "어드민 이벤트 관리 API")
@RestController
@RequestMapping("/api/v1/admin/events")
@RequiredArgsConstructor
public class AdminEventController {

    private final AiEventGenerateService aiEventGenerateService;

    @Operation(summary = "AI 이벤트 구성 자동 생성")
    @PostMapping("/ai-generate")
    public Mono<ResponseEntity<AiGenerateResponse>> aiGenerate(@Valid @RequestBody AiGenerateRequest request) {
        return aiEventGenerateService.generate(request.getPrompt())
                .map(ResponseEntity::ok);
    }
}
