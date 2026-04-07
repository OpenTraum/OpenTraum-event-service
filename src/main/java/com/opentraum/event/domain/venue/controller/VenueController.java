package com.opentraum.event.domain.venue.controller;

import com.opentraum.event.domain.venue.entity.Venue;
import com.opentraum.event.domain.venue.repository.VenueRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Tag(name = "Admin - Venues", description = "공연장 프리셋 API")
@RestController
@RequestMapping("/api/v1/admin/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueRepository venueRepository;

    @Operation(summary = "공연장 프리셋 목록 조회")
    @GetMapping
    public Mono<ResponseEntity<List<Venue>>> getVenues() {
        return venueRepository.findAll()
                .collectList()
                .map(ResponseEntity::ok);
    }
}
