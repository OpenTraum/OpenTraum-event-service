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
    private LocalDateTime ticketOpenAt;
    private String status;
    private String trackPolicy;

    public static ScheduleInfo from(Schedule schedule) {
        return ScheduleInfo.builder()
                .id(schedule.getId())
                .concertId(schedule.getConcertId())
                .ticketOpenAt(schedule.getTicketOpenAt())
                .status(schedule.getStatus())
                .trackPolicy(schedule.getTrackPolicy())
                .build();
    }
}
