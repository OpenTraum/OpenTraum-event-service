package com.opentraum.event.domain.dto;

import com.opentraum.event.domain.entity.Event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String tenantId,
        String title,
        String description,
        String venue,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Integer totalSeats,
        Integer availableSeats,
        BigDecimal price,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getTenantId(),
                event.getTitle(),
                event.getDescription(),
                event.getVenue(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTotalSeats(),
                event.getAvailableSeats(),
                event.getPrice(),
                event.getStatus(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
