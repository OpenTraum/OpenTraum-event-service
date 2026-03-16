package com.opentraum.event.domain.dto;

import com.opentraum.event.domain.entity.Seat;

import java.math.BigDecimal;

public record SeatResponse(
        Long id,
        Long eventId,
        String section,
        String row,
        Integer number,
        String grade,
        BigDecimal price,
        String status
) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getEventId(),
                seat.getSection(),
                seat.getRow(),
                seat.getNumber(),
                seat.getGrade(),
                seat.getPrice(),
                seat.getStatus()
        );
    }
}
