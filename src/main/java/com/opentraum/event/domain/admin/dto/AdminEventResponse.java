package com.opentraum.event.domain.admin.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminEventResponse {

    private Long concertId;
    private Long scheduleId;
    private String title;
    private String artist;
    private String venue;
    private String tenantId;
    private LocalDateTime dateTime;
    private Integer totalSeats;
    private LocalDateTime ticketOpenAt;
    private LocalDateTime ticketCloseAt;
    private String trackPolicy;
    private String imageUrl;
    private String category;
    private String status;
    private List<GradeInfo> grades;
    private List<ZoneInfo> zones;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GradeInfo {
        private String grade;
        private Integer price;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZoneInfo {
        private String zone;
        private String grade;
        private Integer seatCount;
    }
}
