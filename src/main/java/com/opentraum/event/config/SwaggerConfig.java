package com.opentraum.event.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openTraumEventOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OpenTraum Event Service API")
                        .description("멀티 테넌시 이벤트/공연 관리 서비스 API")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("OpenTraum Team")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8083")
                                .description("로컬 개발 서버")
                ))
                .components(new Components());
    }
}
