package com.opentraum.event.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class AdminEventCreateRequest {

    @NotBlank
    private String title;
    private String artist;
    @NotBlank
    private String venue;
    @NotNull
    private LocalDateTime dateTime;
    @NotNull
    @Positive
    private Integer totalSeats;
    @NotNull
    private LocalDateTime ticketOpenAt;
    @NotNull
    private LocalDateTime ticketCloseAt;
    @NotBlank
    private String trackPolicy;

    private String imageUrl;
    private String organizerName;

    @NotEmpty
    private List<GradeInput> grades;
    @NotEmpty
    private List<ZoneInput> zones;

    @Getter
    @NoArgsConstructor
    public static class GradeInput {
        private String grade;
        private Integer price;
        private Integer seatCount;
    }

    @Getter
    @NoArgsConstructor
    public static class ZoneInput {
        private String zone;
        private String grade;
        private Integer seatCount;
    }
}
