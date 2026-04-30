package com.opentraum.event.domain.internal.dto;

import com.opentraum.event.domain.concert.entity.Schedule;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ScheduleInfo {
    private Long id;
    private Long concertId;
    private String tenantId;
    private LocalDateTime ticketOpenAt;
    private String status;
    private String trackPolicy;
    private Integer totalSeats;

    public static ScheduleInfo from(Schedule schedule) {
        return ScheduleInfo.builder()
                .id(schedule.getId())
                .concertId(schedule.getConcertId())
                .tenantId(schedule.getTenantId())
                .ticketOpenAt(schedule.getTicketOpenAt())
                .status(schedule.getStatus())
                .trackPolicy(schedule.getTrackPolicy())
                .totalSeats(schedule.getTotalSeats())
                .build();
    }
}
