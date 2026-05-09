package com.cinema.gateway.filter;

import com.cinema.gateway.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config>
        implements Ordered {

    // Always public — no token required for any HTTP method
    private static final List<String> PUBLIC_ANY_METHOD = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/orders/webhook/"
    );

    // Public for GET only — write operations are protected by downstream service security
    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/movies",
            "/api/genres",
            "/api/sessions",
            "/api/halls",
            "/api/food-menu"
    );

    private final JwtUtils jwtUtils;

    @Autowired
    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        super(Config.class);
        this.jwtUtils = jwtUtils;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (isPublicRequest(request)) {
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onUnauthorized(exchange);
            }

            String token = authHeader.substring(7);

            if (!jwtUtils.validateToken(token)) {
                return onUnauthorized(exchange);
            }

            return chain.filter(exchange);
        };
    }

    private boolean isPublicRequest(ServerHttpRequest request) {
        String path = request.getURI().getPath();

        if (PUBLIC_ANY_METHOD.stream().anyMatch(path::startsWith)) {
            return true;
        }

        return HttpMethod.GET.equals(request.getMethod())
                && PUBLIC_GET_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    public static class Config {
    }
}
