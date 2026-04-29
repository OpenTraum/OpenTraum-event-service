package com.opentraum.event.domain.internal.controller;

import com.opentraum.event.domain.internal.dto.GradeSeatCountResponse;
import com.opentraum.event.domain.internal.dto.SeatHoldRequest;
import com.opentraum.event.domain.internal.dto.SeatHoldResponse;
import com.opentraum.event.domain.internal.dto.SeatInfo;
import com.opentraum.event.domain.seat.entity.SeatStatus;
import com.opentraum.event.domain.seat.repository.SeatRepository;
import com.opentraum.event.domain.seat.service.SeatSagaService;
import com.opentraum.event.global.exception.BusinessException;
import com.opentraum.event.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * reservation-service 등 내부 서비스 간 통신용 좌석 API.
 */
@Tag(name = "Internal - Seat", description = "내부 서비스 간 통신용 좌석 API")
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/seats")
@RequiredArgsConstructor
public class InternalSeatController {

    private final SeatRepository seatRepository;
    private final SeatSagaService seatSagaService;

    @Operation(summary = "좌석 단건 조회")
    @GetMapping
    public Mono<ResponseEntity<SeatInfo>> getSeat(
            @RequestParam Long scheduleId,
            @RequestParam String zone,
            @RequestParam String seatNumber) {
        return seatRepository.findByScheduleIdAndZoneAndSeatNumber(scheduleId, zone, seatNumber)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NO_AVAILABLE_SEATS)))
                .flatMap(seatSagaService::reconcileExpiredHold)
                .map(SeatInfo::from)
                .map(ResponseEntity::ok);
    }

    /**
     * 호환용 좌석 HOLD 해제 API.
     *
     * <p>신규 HOLD/SOLD 상태 전이는 SAGA 경로에서 처리한다. 이 엔드포인트는 기존 내부 호출자의
     * HELD 좌석 해제 요청만 제한적으로 허용한다.
     */
    @Operation(summary = "좌석 HOLD 해제",
            description = "기존 내부 호출자 호환용 API. status=AVAILABLE 요청만 허용하며 HELD 좌석만 해제한다.")
    @PutMapping("/status")
    public Mono<ResponseEntity<Void>> updateStatus(
            @RequestParam Long scheduleId,
            @RequestParam String zone,
            @RequestParam String seatNumber,
            @RequestParam String status) {
        log.info("Internal seat release request: scheduleId={}, zone={}, seat={}, status={}",
                scheduleId, zone, seatNumber, status);
        String targetStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!SeatStatus.AVAILABLE.name().equals(targetStatus)) {
            log.warn("Internal seat transition rejected: targetStatus={}, scheduleId={}, zone={}, seat={}",
                    status, scheduleId, zone, seatNumber);
            return Mono.error(new BusinessException(ErrorCode.INVALID_INPUT));
        }

        return seatRepository.findByScheduleIdAndZoneAndSeatNumber(scheduleId, zone, seatNumber)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NO_AVAILABLE_SEATS)))
                .flatMap(seatSagaService::reconcileExpiredHold)
                .flatMap(seat -> {
                    if (SeatStatus.AVAILABLE.name().equals(seat.getStatus())) {
                        return Mono.just(ResponseEntity.ok().<Void>build());
                    }
                    if (!SeatStatus.HELD.name().equals(seat.getStatus())) {
                        log.warn("Internal seat release rejected from status={}: scheduleId={}, zone={}, seat={}",
                                seat.getStatus(), scheduleId, zone, seatNumber);
                        return Mono.error(new BusinessException(ErrorCode.INVALID_INPUT));
                    }
                    return seatRepository.releaseHeldByCoordinate(scheduleId, zone, seatNumber)
                            .flatMap(rows -> {
                                if (rows > 0) {
                                    return Mono.just(ResponseEntity.ok().<Void>build());
                                }
                                return seatRepository.findByScheduleIdAndZoneAndSeatNumber(scheduleId, zone, seatNumber)
                                        .flatMap(latest -> SeatStatus.AVAILABLE.name().equals(latest.getStatus())
                                                ? Mono.just(ResponseEntity.ok().<Void>build())
                                                : Mono.error(new BusinessException(ErrorCode.INVALID_INPUT)));
                            });
                });
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
                .flatMap(seatSagaService::reconcileExpiredHold)
                .map(SeatInfo::from);
    }

    /**
     * 좌석 단건 HOLD 동기 진입점.
     *
     * <p>Live 트랙 critical path 의 race condition 을 막기 위해 reservation-service 가 호출한다.
     * 내부에서 Redis SETNX + DB 원자 UPDATE 로 좌석을 잡고 {@code SeatHeld} outbox 를 발행한다.
     *
     * <ul>
     *   <li>200 OK: 좌석 확보 성공. body 에 seat_id + held_until 포함.</li>
     *   <li>409 Conflict: 이미 다른 예약이 HELD/SOLD. 호출자는 즉시 사용자에게 실패 응답을 반환한다.</li>
     * </ul>
     */
    @Operation(summary = "좌석 단건 HOLD (동기)",
            description = "reservation-service 가 Live 트랙 좌석 선택 시 호출. 성공 시 SeatHeld 이벤트 발행까지 완료.")
    @PostMapping("/hold")
    public Mono<ResponseEntity<SeatHoldResponse>> holdSingle(@RequestBody SeatHoldRequest request) {
        Map<String, Object> extra = new HashMap<>();
        if (request.getUserId() != null) extra.put("user_id", request.getUserId());
        if (request.getTrackType() != null) extra.put("track_type", request.getTrackType());
        if (request.getAmount() != null) extra.put("amount", request.getAmount());

        return seatSagaService.holdSingle(
                        request.getScheduleId(),
                        request.getZone(),
                        request.getSeatNumber(),
                        request.getReservationId(),
                        request.getSagaId(),
                        extra)
                .map(seat -> ResponseEntity.ok(SeatHoldResponse.builder()
                        .seatId(seat.getSeatId())
                        .zone(seat.getZone())
                        .seatNumber(seat.getSeatNumber())
                        .build()))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).<SeatHoldResponse>build()));
    }
}
