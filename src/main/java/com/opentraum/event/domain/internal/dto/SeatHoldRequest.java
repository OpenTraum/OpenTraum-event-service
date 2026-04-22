package com.opentraum.event.domain.internal.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeatHoldRequest {
    private Long scheduleId;
    private String zone;
    private String seatNumber;
    private Long reservationId;
    private String sagaId;
    private Long userId;
    private String trackType;
    private Long amount;
}
