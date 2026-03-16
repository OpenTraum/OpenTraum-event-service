package com.opentraum.event.domain.concert.controller;

import com.opentraum.event.domain.concert.dto.ConcertResponse;
import com.opentraum.event.domain.concert.service.ConcertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "Concert", description = "공연 조회 API")
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    @Operation(summary = "전체 공연 목록 조회")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ConcertResponse> getConcerts() {
        return concertService.getConcerts();
    }

    @Operation(summary = "테넌트별 공연 목록 조회")
    @GetMapping(value = "/tenant/{tenantId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ConcertResponse> getConcertsByTenant(@PathVariable String tenantId) {
        return concertService.getConcertsByTenantId(tenantId);
    }

    @Operation(summary = "공연 상세 조회")
    @GetMapping(value = "/{concertId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ConcertResponse> getConcertById(@PathVariable Long concertId) {
        return concertService.getConcertById(concertId);
    }
}
