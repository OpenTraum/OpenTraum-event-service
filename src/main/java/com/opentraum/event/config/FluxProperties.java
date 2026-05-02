package com.opentraum.event.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "flux")
public class FluxProperties {
    private String baseUrl;
    private int width = 1024;
    private int height = 576;
    private int steps = 4;
    private double guidance = 0.0;
    private int candidates = 3;
    private int requestTimeoutSeconds = 60;
}
