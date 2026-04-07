package com.opentraum.event.domain.concert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {

    private Long id;
    private String date;   // yyyy-MM-dd
    private String time;   // HH:mm
    private String venue;
    private boolean available;  // schedules.status == "OPEN"
}
