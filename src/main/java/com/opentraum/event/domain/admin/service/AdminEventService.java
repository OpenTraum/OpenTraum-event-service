package com.opentraum.event.domain.admin.service;

import com.opentraum.event.domain.admin.dto.*;
import com.opentraum.event.domain.concert.entity.*;
import com.opentraum.event.domain.concert.repository.*;
import com.opentraum.event.domain.seat.entity.Seat;
import com.opentraum.event.domain.seat.entity.SeatStatus;
import com.opentraum.event.domain.seat.repository.SeatRepository;
import com.opentraum.event.global.exception.BusinessException;
import com.opentraum.event.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminEventService {

    private final ConcertRepository concertRepository;
    private final ScheduleRepository scheduleRepository;
    private final GradeRepository gradeRepository;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;

    public Mono<AdminEventResponse> createEvent(String tenantId, AdminEventCreateRequest request) {
        Concert concert = Concert.builder()
                .title(request.getTitle())
                .artist(request.getArtist())
                .venue(request.getVenue())
                .tenantId(tenantId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return concertRepository.save(concert)
                .flatMap(savedConcert -> {
                    Schedule schedule = Schedule.builder()
                            .concertId(savedConcert.getId())
                            .dateTime(request.getDateTime())
                            .totalSeats(request.getTotalSeats())
                            .ticketOpenAt(request.getTicketOpenAt())
                            .ticketCloseAt(request.getTicketCloseAt())
                            .trackPolicy(request.getTrackPolicy())
                            .status(ScheduleStatus.UPCOMING.name())
                            .createdAt(LocalDateTime.now())
                            .build();

                    return scheduleRepository.save(schedule)
                            .flatMap(savedSchedule -> {
                                // grade name → price 매핑
                                Map<String, Integer> gradePriceMap = request.getGrades().stream()
                                        .collect(Collectors.toMap(
                                                AdminEventCreateRequest.GradeInput::getGrade,
                                                AdminEventCreateRequest.GradeInput::getPrice));

                                // Grade 저장
                                Mono<List<Grade>> gradesMono = Flux.fromIterable(request.getGrades())
                                        .map(g -> Grade.builder()
                                                .scheduleId(savedSchedule.getId())
                                                .grade(g.getGrade())
                                                .price(g.getPrice())
                                                .build())
                                        .flatMap(gradeRepository::save)
                                        .collectList();

                                // Zone 저장
                                Mono<List<Zone>> zonesMono = Flux.fromIterable(request.getZones())
                                        .map(z -> Zone.builder()
                                                .scheduleId(savedSchedule.getId())
                                                .zone(z.getZone())
                                                .grade(z.getGrade())
                                                .seatCount(z.getSeatCount())
                                                .build())
                                        .flatMap(zoneRepository::save)
                                        .collectList();

                                // Seat 생성
                                Mono<Void> seatsMono = Flux.fromIterable(request.getZones())
                                        .flatMap(z -> {
                                            Integer price = gradePriceMap.getOrDefault(z.getGrade(), 0);
                                            List<Seat> seats = new ArrayList<>();
                                            for (int i = 1; i <= z.getSeatCount(); i++) {
                                                seats.add(Seat.builder()
                                                        .scheduleId(savedSchedule.getId())
                                                        .grade(z.getGrade())
                                                        .zone(z.getZone())
                                                        .seatNumber(String.valueOf(i))
                                                        .price(price)
                                                        .status(SeatStatus.AVAILABLE.name())
                                                        .createdAt(LocalDateTime.now())
                                                        .build());
                                            }
                                            return seatRepository.saveAll(seats).then();
                                        })
                                        .then();

                                return Mono.zip(gradesMono, zonesMono, seatsMono.thenReturn(true))
                                        .map(tuple -> buildResponse(savedConcert, savedSchedule, tuple.getT1(), tuple.getT2()));
                            });
                })
                .doOnSuccess(r -> log.info("이벤트 생성 완료: concertId={}, scheduleId={}", r.getConcertId(), r.getScheduleId()));
    }

    public Mono<AdminEventResponse> getEvent(String tenantId, Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                .flatMap(schedule -> concertRepository.findById(schedule.getConcertId())
                        .filter(concert -> tenantId.equals(concert.getTenantId()))
                        .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                        .flatMap(concert -> Mono.zip(
                                gradeRepository.findByScheduleId(scheduleId).collectList(),
                                zoneRepository.findByScheduleId(scheduleId).collectList()
                        ).map(tuple -> buildResponse(concert, schedule, tuple.getT1(), tuple.getT2()))));
    }

    public Flux<AdminEventResponse> listEvents(String tenantId) {
        return concertRepository.findByTenantId(tenantId)
                .flatMap(concert -> scheduleRepository.findByConcertId(concert.getId())
                        .map(schedule -> AdminEventResponse.builder()
                                .concertId(concert.getId())
                                .scheduleId(schedule.getId())
                                .title(concert.getTitle())
                                .artist(concert.getArtist())
                                .venue(concert.getVenue())
                                .tenantId(concert.getTenantId())
                                .dateTime(schedule.getDateTime())
                                .totalSeats(schedule.getTotalSeats())
                                .ticketOpenAt(schedule.getTicketOpenAt())
                                .ticketCloseAt(schedule.getTicketCloseAt())
                                .trackPolicy(schedule.getTrackPolicy())
                                .status(schedule.getStatus())
                                .build()));
    }

    public Mono<AdminEventResponse> updateEvent(String tenantId, Long scheduleId, AdminEventCreateRequest request) {
        return scheduleRepository.findById(scheduleId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                .flatMap(schedule -> concertRepository.findById(schedule.getConcertId())
                        .filter(concert -> tenantId.equals(concert.getTenantId()))
                        .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                        .flatMap(concert -> {
                            concert.setTitle(request.getTitle());
                            concert.setArtist(request.getArtist());
                            concert.setVenue(request.getVenue());
                            concert.setUpdatedAt(LocalDateTime.now());

                            schedule.setDateTime(request.getDateTime());
                            schedule.setTotalSeats(request.getTotalSeats());
                            schedule.setTicketOpenAt(request.getTicketOpenAt());
                            schedule.setTicketCloseAt(request.getTicketCloseAt());
                            schedule.setTrackPolicy(request.getTrackPolicy());

                            return Mono.zip(
                                    concertRepository.save(concert),
                                    scheduleRepository.save(schedule)
                            );
                        })
                        .flatMap(tuple -> getEvent(tenantId, scheduleId)));
    }

    public Mono<Void> deleteEvent(String tenantId, Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                .flatMap(schedule -> concertRepository.findById(schedule.getConcertId())
                        .filter(concert -> tenantId.equals(concert.getTenantId()))
                        .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                        .then(seatRepository.deleteByScheduleId(scheduleId)
                        .then(zoneRepository.deleteByScheduleId(scheduleId))
                        .then(gradeRepository.deleteByScheduleId(scheduleId))
                        .then(scheduleRepository.deleteById(scheduleId))
                        .then(concertRepository.deleteById(schedule.getConcertId()))))
                .doOnSuccess(v -> log.info("이벤트 삭제 완료: scheduleId={}", scheduleId));
    }

    public Mono<AdminDashboardResponse> getDashboard(String tenantId, Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                .flatMap(schedule -> concertRepository.findById(schedule.getConcertId())
                        .filter(concert -> tenantId.equals(concert.getTenantId()))
                        .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND)))
                        .flatMap(concert -> Mono.zip(
                                seatRepository.findSeatCountByScheduleIdGroupByGrade(scheduleId).collectList(),
                                seatRepository.findSoldSeatCountByScheduleIdGroupByGrade(scheduleId).collectList()
                        ).map(tuple -> {
                            Map<String, Long> totalByGrade = tuple.getT1().stream()
                                    .collect(Collectors.toMap(g -> g.getGrade(), g -> g.getCount()));
                            Map<String, Long> soldByGrade = tuple.getT2().stream()
                                    .collect(Collectors.toMap(g -> g.getGrade(), g -> g.getCount()));

                            long totalSold = soldByGrade.values().stream().mapToLong(Long::longValue).sum();
                            long totalAvailable = schedule.getTotalSeats() - totalSold;

                            List<AdminDashboardResponse.GradeStat> gradeStats = totalByGrade.entrySet().stream()
                                    .map(e -> {
                                        long sold = soldByGrade.getOrDefault(e.getKey(), 0L);
                                        return AdminDashboardResponse.GradeStat.builder()
                                                .grade(e.getKey())
                                                .totalSeats(e.getValue())
                                                .soldSeats(sold)
                                                .availableSeats(e.getValue() - sold)
                                                .build();
                                    })
                                    .toList();

                            return AdminDashboardResponse.builder()
                                    .scheduleId(scheduleId)
                                    .title(concert.getTitle())
                                    .status(schedule.getStatus())
                                    .totalSeats(schedule.getTotalSeats())
                                    .soldSeats(totalSold)
                                    .availableSeats(totalAvailable)
                                    .gradeStats(gradeStats)
                                    .build();
                        })));
    }

    private AdminEventResponse buildResponse(Concert concert, Schedule schedule, List<Grade> grades, List<Zone> zones) {
        return AdminEventResponse.builder()
                .concertId(concert.getId())
                .scheduleId(schedule.getId())
                .title(concert.getTitle())
                .artist(concert.getArtist())
                .venue(concert.getVenue())
                .tenantId(concert.getTenantId())
                .dateTime(schedule.getDateTime())
                .totalSeats(schedule.getTotalSeats())
                .ticketOpenAt(schedule.getTicketOpenAt())
                .ticketCloseAt(schedule.getTicketCloseAt())
                .trackPolicy(schedule.getTrackPolicy())
                .status(schedule.getStatus())
                .grades(grades.stream()
                        .map(g -> AdminEventResponse.GradeInfo.builder()
                                .grade(g.getGrade())
                                .price(g.getPrice())
                                .build())
                        .toList())
                .zones(zones.stream()
                        .map(z -> AdminEventResponse.ZoneInfo.builder()
                                .zone(z.getZone())
                                .grade(z.getGrade())
                                .seatCount(z.getSeatCount())
                                .build())
                        .toList())
                .build();
    }
}
