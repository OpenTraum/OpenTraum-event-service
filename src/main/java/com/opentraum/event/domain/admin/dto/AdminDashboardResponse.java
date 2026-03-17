package com.opentraum.event.domain.admin.dto;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {

    private Long scheduleId;
    private String title;
    private String status;
    private Integer totalSeats;
    private Long soldSeats;
    private Long availableSeats;
    private List<GradeStat> gradeStats;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GradeStat {
        private String grade;
        private Long totalSeats;
        private Long soldSeats;
        private Long availableSeats;
    }
}
