package com.opentraum.event.domain.internal.controller;

import com.opentraum.event.domain.internal.dto.GradeSeatCountResponse;
import com.opentraum.event.domain.internal.dto.SeatHoldRequest;
import com.opentraum.event.domain.internal.dto.SeatHoldResponse;
import com.opentraum.event.domain.internal.dto.SeatInfo;
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
     * @deprecated SAGA 전환 이후 좌석 상태는 Kafka 이벤트(ReservationCreated / PaymentCompleted /
     * PaymentFailed 등)로만 변경한다. 이 REST 엔드포인트는 호환성 유지용으로만 남아있으며 Wave 3에서
     * 제거 예정.
     */
    @Deprecated
    @Operation(summary = "[Deprecated] 좌석 상태 변경 — SAGA 이벤트 기반으로 전환됨",
            description = "SAGA 전환 이후 직접 호출 금지. Kafka 이벤트로만 상태 전이.")
    @PutMapping("/status")
    public Mono<ResponseEntity<Void>> updateStatus(
            @RequestParam Long scheduleId,
            @RequestParam String zone,
            @RequestParam String seatNumber,
            @RequestParam String status) {
        log.warn("[DEPRECATED] /api/v1/internal/seats/status direct call: scheduleId={}, zone={}, seat={}, status={}. "
                        + "SAGA 전환 이후 이 경로는 제거 대상이다.",
                scheduleId, zone, seatNumber, status);
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
