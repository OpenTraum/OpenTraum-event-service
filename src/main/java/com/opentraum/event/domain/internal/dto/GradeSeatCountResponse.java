package com.opentraum.event.domain.internal.dto;

import com.opentraum.event.domain.seat.dto.GradeSeatCount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GradeSeatCountResponse {
    private String grade;
    private Long count;

    public static GradeSeatCountResponse from(GradeSeatCount gsc) {
        return GradeSeatCountResponse.builder()
                .grade(gsc.getGrade())
                .count(gsc.getCount())
                .build();
    }
}
