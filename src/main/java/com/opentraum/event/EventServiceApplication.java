package com.opentraum.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EventServiceApplication {

    public static void main(String[] args) {
        // Micrometer Tracing + Reactor: traceId/spanId 전파를 위해 자동 컨텍스트 전파 활성화.
        reactor.core.publisher.Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(EventServiceApplication.class, args);
    }
}
