package com.opentraum.event.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("events")
public class Event {

    @Id
    private Long id;

    @Column("tenant_id")
    private String tenantId;

    private String title;

    private String description;

    private String venue;

    @Column("start_date")
    private LocalDateTime startDate;

    @Column("end_date")
    private LocalDateTime endDate;

    @Column("total_seats")
    private Integer totalSeats;

    @Column("available_seats")
    private Integer availableSeats;

    private BigDecimal price;

    private String status;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
