package com.opentraum.event.domain.concert.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Schedule {

    @Id
    private Long id;

    private Long concertId;

    private LocalDateTime dateTime;

    private Integer totalSeats;

    private LocalDateTime ticketOpenAt;

    private LocalDateTime ticketCloseAt;

    private String status;

    private String trackPolicy;

    private LocalDateTime createdAt;
}
