package com.opentraum.event.domain.seat.repository;

import com.opentraum.event.domain.seat.dto.GradeSeatCount;
import com.opentraum.event.domain.seat.entity.Seat;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SeatRepository extends ReactiveCrudRepository<Seat, Long> {

    @Modifying
    @Query("UPDATE seats SET status = :status WHERE schedule_id = :scheduleId AND zone = :zone AND seat_number = :seatNumber")
    Mono<Integer> updateStatusByScheduleIdAndZoneAndSeatNumber(
            @Param("status") String status,
            @Param("scheduleId") Long scheduleId,
            @Param("zone") String zone,
            @Param("seatNumber") String seatNumber);

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
