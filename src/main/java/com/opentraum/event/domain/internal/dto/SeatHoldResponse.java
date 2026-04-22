package com.opentraum.event.domain.internal.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeatHoldResponse {
    private Long seatId;
    private String zone;
    private String seatNumber;
}
