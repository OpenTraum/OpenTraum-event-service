package com.opentraum.event.domain.concert.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("grades")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Grade {

    @Id
    private Long id;

    private Long scheduleId;

    private String grade;

    private Integer price;
}
