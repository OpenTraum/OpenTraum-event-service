package com.opentraum.event.domain.venue.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("venues")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Venue {

    @Id
    private Long id;

    private String name;

    private Integer totalSeats;

    private String zoneConfig;

    private LocalDateTime createdAt;
}
