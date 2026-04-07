package com.opentraum.event.domain.venue.repository;

import com.opentraum.event.domain.venue.entity.Venue;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface VenueRepository extends ReactiveCrudRepository<Venue, Long> {
}
