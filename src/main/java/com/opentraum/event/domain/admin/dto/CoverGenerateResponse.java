package com.opentraum.event.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CoverGenerateResponse {
    private List<String> urls;
    private boolean fallback;
    private String model;
    private double seconds;
}
