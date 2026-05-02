package com.opentraum.event.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CoverGenerateRequest {
    @NotBlank
    private String category;

    @NotBlank
    private String title;

    @NotBlank
    private String venue;

    private String artist;

    private String dateTime;
}
