package com.opentraum.event.domain.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 등급별 좌석 수 집계 결과 (GROUP BY grade 쿼리용)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GradeSeatCount {
    private String grade;
    private Long count;
}
