package com.opentraum.event.domain.concert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConcertResponse {

    private Long id;
    private String title;
    private String artist;
    private String venue;
    private String tenantId;
    private String saleStatus;   // "on-sale" / "coming-soon" / "sold-out"
    private String saleDate;     // UPCOMING일 때만, ISO-8601 (null 가능)
    private List<ScheduleResponse> dates;
    private List<GradeDetailResponse> grades;
}
