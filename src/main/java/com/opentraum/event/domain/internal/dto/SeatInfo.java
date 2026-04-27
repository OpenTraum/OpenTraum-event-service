package com.opentraum.event.domain.internal.dto;

import com.opentraum.event.domain.seat.entity.Seat;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeatInfo {
    private Long id;
    private Long scheduleId;
    private String zone;
    private String seatNumber;
    private String grade;
    private String status;
    private Integer price;

    public static SeatInfo from(Seat seat) {
        return SeatInfo.builder()
                .id(seat.getId())
                .scheduleId(seat.getScheduleId())
                .zone(seat.getZone())
                .seatNumber(seat.getSeatNumber())
                .grade(seat.getGrade())
                .status(seat.getStatus())
                .price(seat.getPrice())
                .build();
    }
}
