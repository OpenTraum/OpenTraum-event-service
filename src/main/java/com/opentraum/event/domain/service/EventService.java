package com.opentraum.event.domain.service;

import com.opentraum.event.domain.dto.EventCreateRequest;
import com.opentraum.event.domain.dto.EventResponse;
import com.opentraum.event.domain.dto.SeatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventService {

    Mono<EventResponse> createEvent(EventCreateRequest request);

    Mono<EventResponse> getEventById(Long id);

    Flux<EventResponse> getEventsByTenantId(String tenantId);

    Flux<EventResponse> searchEvents(String tenantId, String keyword);

    Mono<EventResponse> updateEvent(Long id, EventCreateRequest request);

    Mono<Void> deleteEvent(Long id);

    Flux<SeatResponse> getSeatsByEventId(Long eventId);

    Flux<SeatResponse> getAvailableSeatsByEventId(Long eventId);
}
