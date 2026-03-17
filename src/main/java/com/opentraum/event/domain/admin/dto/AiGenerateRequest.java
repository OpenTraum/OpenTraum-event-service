package com.opentraum.event.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiGenerateRequest {

    @NotBlank(message = "프롬프트를 입력해주세요")
    private String prompt;
}
