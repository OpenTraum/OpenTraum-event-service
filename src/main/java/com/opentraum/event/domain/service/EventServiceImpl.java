package com.opentraum.event.domain.service;

import com.opentraum.event.domain.dto.EventCreateRequest;
import com.opentraum.event.domain.dto.EventResponse;
import com.opentraum.event.domain.dto.SeatResponse;
import com.opentraum.event.domain.entity.Event;
import com.opentraum.event.domain.repository.EventRepository;
import com.opentraum.event.domain.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String EVENT_TOPIC = "event-service.events";

    @Override
    @Transactional
    public Mono<EventResponse> createEvent(EventCreateRequest request) {
        Event event = Event.builder()
                .tenantId(request.tenantId())
                .title(request.title())
                .description(request.description())
                .venue(request.venue())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .totalSeats(request.totalSeats())
                .availableSeats(request.totalSeats())
                .price(request.price())
                .status("DRAFT")
                .build();

        return eventRepository.save(event)
                .doOnSuccess(saved -> {
                    log.info("Event created: id={}, tenantId={}, title={}",
                            saved.getId(), saved.getTenantId(), saved.getTitle());
                    kafkaTemplate.send(EVENT_TOPIC, saved.getTenantId(),
                            new EventPublishedMessage("EVENT_CREATED", saved.getId(), saved.getTenantId()));
                })
                .map(EventResponse::from);
    }

    @Override
    public Mono<EventResponse> getEventById(Long id) {
        return eventRepository.findById(id)
                .map(EventResponse::from)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)));
    }

    @Override
    public Flux<EventResponse> getEventsByTenantId(String tenantId) {
        return eventRepository.findByTenantId(tenantId)
                .map(EventResponse::from);
    }

    @Override
    public Flux<EventResponse> searchEvents(String tenantId, String keyword) {
        return eventRepository.searchByTenantIdAndTitle(tenantId, keyword)
                .map(EventResponse::from);
    }

    @Override
    @Transactional
    public Mono<EventResponse> updateEvent(Long id, EventCreateRequest request) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(existing -> {
                    existing.setTitle(request.title());
                    existing.setDescription(request.description());
                    existing.setVenue(request.venue());
                    existing.setStartDate(request.startDate());
                    existing.setEndDate(request.endDate());
                    existing.setTotalSeats(request.totalSeats());
                    existing.setPrice(request.price());
                    return eventRepository.save(existing);
                })
                .doOnSuccess(updated -> {
                    log.info("Event updated: id={}, title={}", updated.getId(), updated.getTitle());
                    kafkaTemplate.send(EVENT_TOPIC, updated.getTenantId(),
                            new EventPublishedMessage("EVENT_UPDATED", updated.getId(), updated.getTenantId()));
                })
                .map(EventResponse::from);
    }

    @Override
    @Transactional
    public Mono<Void> deleteEvent(Long id) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    log.info("Event deleted: id={}, tenantId={}", event.getId(), event.getTenantId());
                    kafkaTemplate.send(EVENT_TOPIC, event.getTenantId(),
                            new EventPublishedMessage("EVENT_DELETED", event.getId(), event.getTenantId()));
                    return eventRepository.deleteById(id);
                });
    }

    @Override
    public Flux<SeatResponse> getSeatsByEventId(Long eventId) {
        return seatRepository.findByEventId(eventId)
                .map(SeatResponse::from);
    }

    @Override
    public Flux<SeatResponse> getAvailableSeatsByEventId(Long eventId) {
        return seatRepository.findByEventIdAndStatus(eventId, "AVAILABLE")
                .map(SeatResponse::from);
    }

    private record EventPublishedMessage(String type, Long eventId, String tenantId) {
    }
}
