package com.opentraum.event.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("seats")
public class Seat {

    @Id
    private Long id;

    @Column("event_id")
    private Long eventId;

    private String section;

    @Column("seat_row")
    private String row;

    @Column("seat_number")
    private Integer number;

    private String grade;

    private BigDecimal price;

    private String status;
}
