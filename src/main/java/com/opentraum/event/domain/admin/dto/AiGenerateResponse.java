package com.opentraum.event.domain.admin.dto;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateResponse {

    private String title;
    private String artist;
    private String venue;
    private String dateTime;
    private Integer totalSeats;
    private String trackPolicy;
    private String category;
    private List<GradeConfig> grades;
    private List<ZoneConfig> zones;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GradeConfig {
        private String grade;
        private Integer price;
        private Integer seatCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZoneConfig {
        private String zone;
        private String grade;
        private Integer seatCount;
    }
}
