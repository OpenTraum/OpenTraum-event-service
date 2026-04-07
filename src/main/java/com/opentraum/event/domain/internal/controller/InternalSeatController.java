package com.opentraum.event.domain.internal.controller;

import com.opentraum.event.domain.internal.dto.GradeSeatCountResponse;
import com.opentraum.event.domain.internal.dto.SeatInfo;
import com.opentraum.event.domain.seat.repository.SeatRepository;
import com.opentraum.event.global.exception.BusinessException;
import com.opentraum.event.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * reservation-service 등 내부 서비스 간 통신용 좌석 API.
 */
@Tag(name = "Internal - Seat", description = "내부 서비스 간 통신용 좌석 API")
@RestController
@RequestMapping("/api/v1/internal/seats")
@RequiredArgsConstructor
public class InternalSeatController {

    private final SeatRepository seatRepository;

    @Operation(summary = "좌석 단건 조회")
    @GetMapping
    public Mono<ResponseEntity<SeatInfo>> getSeat(
            @RequestParam Long scheduleId,
            @RequestParam String zone,
            @RequestParam String seatNumber) {
        return seatRepository.findByScheduleIdAndZoneAndSeatNumber(scheduleId, zone, seatNumber)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NO_AVAILABLE_SEATS)))
                .map(SeatInfo::from)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "좌석 상태 변경")
    @PutMapping("/status")
    public Mono<ResponseEntity<Void>> updateStatus(
            @RequestParam Long scheduleId,
            @RequestParam String zone,
            @RequestParam String seatNumber,
            @RequestParam String status) {
        return seatRepository.updateStatusByScheduleIdAndZoneAndSeatNumber(status, scheduleId, zone, seatNumber)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @Operation(summary = "등급별 좌석 수")
    @GetMapping("/count")
    public Mono<ResponseEntity<Long>> countByGrade(
            @RequestParam Long scheduleId,
            @RequestParam String grade) {
        return seatRepository.countByScheduleIdAndGrade(scheduleId, grade)
                .defaultIfEmpty(0L)
                .map(ResponseEntity::ok);
    }

    @Operation(summary = "등급별 좌석 수 (그룹)")
    @GetMapping("/count-by-grade")
    public Flux<GradeSeatCountResponse> countByGradeGrouped(@RequestParam Long scheduleId) {
        return seatRepository.findSeatCountByScheduleIdGroupByGrade(scheduleId)
                .map(GradeSeatCountResponse::from);
    }

    @Operation(summary = "좌석 일괄 조회")
    @GetMapping("/batch")
    public Flux<SeatInfo> getBatch(
            @RequestParam Long scheduleId,
            @RequestParam String zone,
            @RequestParam String seatNumbers) {
        List<String> numbers = Arrays.asList(seatNumbers.split(","));
        return seatRepository.findByScheduleIdAndZoneAndSeatNumberIn(scheduleId, zone, numbers)
                .map(SeatInfo::from);
    }
}
