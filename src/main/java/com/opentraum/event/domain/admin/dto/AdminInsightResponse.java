package com.opentraum.event.domain.admin.dto;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminInsightResponse {

    private Long scheduleId;
    private String riskLevel;  // LOW, MEDIUM, HIGH, CRITICAL
    private List<String> insights;
    private List<RecommendedAction> actions;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedAction {
        private String type;       // PRICE_ADJUST, TRACK_CHANGE, CAPACITY_WARNING, MARKETING
        private String title;
        private String description;
        private String urgency;    // LOW, MEDIUM, HIGH
    }
}
