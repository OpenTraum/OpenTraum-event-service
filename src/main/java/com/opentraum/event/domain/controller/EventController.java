package com.opentraum.event.domain.controller;

import com.opentraum.event.domain.dto.EventCreateRequest;
import com.opentraum.event.domain.dto.EventResponse;
import com.opentraum.event.domain.dto.SeatResponse;
import com.opentraum.event.domain.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<EventResponse> createEvent(@Valid @RequestBody EventCreateRequest request) {
        return eventService.createEvent(request);
    }

    @GetMapping("/{id}")
    public Mono<EventResponse> getEvent(@PathVariable Long id) {
        return eventService.getEventById(id);
    }

    @GetMapping
    public Flux<EventResponse> getEventsByTenant(@RequestParam String tenantId) {
        return eventService.getEventsByTenantId(tenantId);
    }

    @GetMapping("/search")
    public Flux<EventResponse> searchEvents(
            @RequestParam String tenantId,
            @RequestParam String keyword) {
        return eventService.searchEvents(tenantId, keyword);
    }

    @PutMapping("/{id}")
    public Mono<EventResponse> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody EventCreateRequest request) {
        return eventService.updateEvent(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteEvent(@PathVariable Long id) {
        return eventService.deleteEvent(id);
    }

    @GetMapping("/{eventId}/seats")
    public Flux<SeatResponse> getSeats(@PathVariable Long eventId) {
        return eventService.getSeatsByEventId(eventId);
    }

    @GetMapping("/{eventId}/seats/available")
    public Flux<SeatResponse> getAvailableSeats(@PathVariable Long eventId) {
        return eventService.getAvailableSeatsByEventId(eventId);
    }
}
