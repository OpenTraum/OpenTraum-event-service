package com.opentraum.event.domain.seat.repository;

import com.opentraum.event.domain.seat.dto.GradeSeatCount;
import com.opentraum.event.domain.seat.entity.Seat;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatRepository extends ReactiveCrudRepository<Seat, Long> {

    @Modifying
    @Query("UPDATE seats SET status = :status WHERE schedule_id = :scheduleId AND zone = :zone AND seat_number = :seatNumber")
    Mono<Integer> updateStatusByScheduleIdAndZoneAndSeatNumber(
            @Param("status") String status,
            @Param("scheduleId") Long scheduleId,
            @Param("zone") String zone,
            @Param("seatNumber") String seatNumber);

    /**
     * 좌석 상태 AVAILABLE -> HELD 전이 (원자적).
     *
     * <p>조건: status='AVAILABLE' 이거나, status='HELD'인데 held_until이 이미 지난 경우만 성공.
     * 반환값이 1이면 이 호출이 HOLD를 차지한 것, 0이면 다른 세션이 이미 HOLD/SOLD 중.
     */
    @Modifying
    @Query("""
            UPDATE seats
               SET status = 'HELD',
                   held_until = :heldUntil,
                   reservation_id = :reservationId
             WHERE schedule_id = :scheduleId
               AND zone = :zone
               AND seat_number = :seatNumber
               AND (status = 'AVAILABLE'
                    OR (status = 'HELD' AND held_until < :now))
            """)
    Mono<Integer> tryHold(
            @Param("scheduleId") Long scheduleId,
            @Param("zone") String zone,
            @Param("seatNumber") String seatNumber,
            @Param("reservationId") Long reservationId,
            @Param("heldUntil") LocalDateTime heldUntil,
            @Param("now") LocalDateTime now);

    /**
     * 좌석 HELD -> SOLD 전이. reservation_id 기준으로 소유권 확인.
     */
    @Modifying
    @Query("""
            UPDATE seats
               SET status = 'SOLD',
                   held_until = NULL
             WHERE schedule_id = :scheduleId
               AND zone = :zone
               AND seat_number = :seatNumber
               AND reservation_id = :reservationId
               AND status = 'HELD'
            """)
    Mono<Integer> confirmSold(
            @Param("scheduleId") Long scheduleId,
            @Param("zone") String zone,
            @Param("seatNumber") String seatNumber,
            @Param("reservationId") Long reservationId);

    /**
     * Lottery 당첨 좌석 확정: AVAILABLE -> SOLD + reservation_id 세팅.
     *
     * <p>Lottery 트랙은 HOLD 단계를 거치지 않고 reservation-service가 Fisher-Yates 로 확정한
     * 좌석을 바로 SOLD 시킨다. 경합이 없다는 전제는 {@code lottery-paid:*} 동기화로 보장되며,
     * 다른 세션이 이미 HELD/SOLD 이라면 0을 반환해 무시된다(idempotency / 중복 배정 방지).
     */
    @Modifying
    @Query("""
            UPDATE seats
               SET status = 'SOLD',
                   reservation_id = :reservationId,
                   held_until = NULL
             WHERE schedule_id = :scheduleId
               AND zone = :zone
               AND seat_number = :seatNumber
               AND status = 'AVAILABLE'
            """)
    Mono<Integer> confirmSoldFromAvailable(
            @Param("scheduleId") Long scheduleId,
            @Param("zone") String zone,
            @Param("seatNumber") String seatNumber,
            @Param("reservationId") Long reservationId);

    /**
     * 좌석 HELD/SOLD -> AVAILABLE. reservation_id 기준 소유권 확인.
     * 보상 트랜잭션 경로에서 SOLD도 되돌려야 하므로 status 조건은 넣지 않는다.
     */
    @Modifying
    @Query("""
            UPDATE seats
               SET status = 'AVAILABLE',
                   held_until = NULL,
                   reservation_id = NULL
             WHERE schedule_id = :scheduleId
               AND zone = :zone
               AND seat_number = :seatNumber
               AND reservation_id = :reservationId
            """)
    Mono<Integer> releaseByReservation(
            @Param("scheduleId") Long scheduleId,
            @Param("zone") String zone,
            @Param("seatNumber") String seatNumber,
            @Param("reservationId") Long reservationId);

    /**
     * reservation_id 로 점유된 전체 좌석 조회 (release 보상 시 좌석 좌표를 모를 때).
     */
    Flux<Seat> findByReservationId(Long reservationId);

    /**
     * 만료된 HELD 자동 보정. status='HELD' AND held_until < now 조건으로만 AVAILABLE 전이.
     *
     * <p>스케줄러/조회 보정 진입점 공용. reservation_id 모를 때도 안전하게 호출 가능.
     */
    @Modifying
    @Query("""
            UPDATE seats
               SET status = 'AVAILABLE',
                   held_until = NULL,
                   reservation_id = NULL
             WHERE id = :seatId
               AND status = 'HELD'
               AND held_until < :now
            """)
    Mono<Integer> expireHoldIfStale(@Param("seatId") Long seatId, @Param("now") LocalDateTime now);

    Mono<Long> countByScheduleIdAndGrade(Long scheduleId, String grade);

    Flux<Seat> findByScheduleId(Long scheduleId);

    Flux<Seat> findByScheduleIdAndZone(Long scheduleId, String zone);

    Mono<Seat> findByScheduleIdAndZoneAndSeatNumber(Long scheduleId, String zone, String seatNumber);

    Flux<Seat> findByScheduleIdAndZoneAndSeatNumberIn(Long scheduleId, String zone, List<String> seatNumbers);

    @Query("SELECT grade, COUNT(*) as count FROM seats WHERE schedule_id = :scheduleId GROUP BY grade")
    Flux<GradeSeatCount> findSeatCountByScheduleIdGroupByGrade(Long scheduleId);

    @Query("SELECT grade, COUNT(*) as count FROM seats WHERE schedule_id = :scheduleId AND status = 'AVAILABLE' GROUP BY grade")
    Flux<GradeSeatCount> findAvailableSeatCountByScheduleIdGroupByGrade(@Param("scheduleId") Long scheduleId);

    @Query("SELECT grade, COUNT(*) as count FROM seats WHERE schedule_id = :scheduleId AND status = 'SOLD' GROUP BY grade")
    Flux<GradeSeatCount> findSoldSeatCountByScheduleIdGroupByGrade(@Param("scheduleId") Long scheduleId);

    Mono<Void> deleteByScheduleId(Long scheduleId);
}
