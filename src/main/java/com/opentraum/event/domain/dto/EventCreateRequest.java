package com.opentraum.event.domain.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventCreateRequest(

        @NotBlank(message = "테넌트 ID는 필수입니다")
        String tenantId,

        @NotBlank(message = "공연 제목은 필수입니다")
        String title,

        String description,

        @NotBlank(message = "공연 장소는 필수입니다")
        String venue,

        @NotNull(message = "시작 일시는 필수입니다")
        @Future(message = "시작 일시는 현재 이후여야 합니다")
        LocalDateTime startDate,

        @NotNull(message = "종료 일시는 필수입니다")
        @Future(message = "종료 일시는 현재 이후여야 합니다")
        LocalDateTime endDate,

        @NotNull(message = "총 좌석 수는 필수입니다")
        @Min(value = 1, message = "총 좌석 수는 1 이상이어야 합니다")
        Integer totalSeats,

        @NotNull(message = "가격은 필수입니다")
        @Positive(message = "가격은 양수여야 합니다")
        BigDecimal price
) {
}
