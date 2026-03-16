package com.opentraum.event.domain.concert.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("zones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Zone {

    @Id
    private Long id;

    private Long scheduleId;

    private String zone;

    private String grade;

    private Integer seatCount;
}
