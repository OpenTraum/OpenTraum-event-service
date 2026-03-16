package com.opentraum.event.domain.seat.controller;

import com.opentraum.event.domain.seat.service.SeatPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Seat Pool", description = "좌석 풀 관리 API (관리자용)")
@RestController
@RequestMapping("/api/v1/admin/seats")
@RequiredArgsConstructor
public class SeatPoolController {

    private final SeatPoolService seatPoolService;

    @Operation(summary = "좌석 풀 초기화")
    @PostMapping("/{scheduleId}/initialize")
    public Mono<ResponseEntity<Void>> initializeSeatPools(
            @Parameter(required = true)
            @PathVariable Long scheduleId) {
        return seatPoolService.initializeSeatPools(scheduleId)
                .then(Mono.just(ResponseEntity.ok().build()));
    }
}
