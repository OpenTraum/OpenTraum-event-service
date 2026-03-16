package com.opentraum.event.domain.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 추첨 좌석 배정 결과 (응답/반환용). 구역 + 좌석 번호.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ZoneSeatAssignmentResponse {
    private String zone;
    private String seatNumber;
}
