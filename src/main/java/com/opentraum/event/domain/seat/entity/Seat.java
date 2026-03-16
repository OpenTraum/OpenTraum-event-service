package com.opentraum.event.domain.seat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("seats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    private Long id;

    private Long scheduleId;

    private String grade;

    private String zone;

    private String seatNumber;

    private Integer price;

    private String status;

    private LocalDateTime createdAt;
}
