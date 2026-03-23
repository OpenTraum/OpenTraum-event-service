package com.opentraum.event.global.filter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AdminRoleFilter implements WebFilter {

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();
    private static final String ADMIN_PATTERN = "/api/v1/admin/**";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (pathMatcher.match(ADMIN_PATTERN, path)) {
            String role = exchange.getRequest().getHeaders().getFirst("X-User-Role");
            if (!"ORGANIZER".equals(role)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        return chain.filter(exchange);
    }
}
