package com.cinema.notification.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// @Configuration — Spring обрабатывает этот класс для регистрации @Bean методов.
@Configuration
// @EnableWebSecurity — включает Spring Security web интеграцию.
@EnableWebSecurity
// @RequiredArgsConstructor — конструктор для final поля jwtAuthFilter.
@RequiredArgsConstructor
// SecurityConfig в notification-service — ПРОСТЕЙШАЯ конфигурация из всех сервисов:
//   - Нет @EnableMethodSecurity (нет @PreAuthorize аннотаций на методах)
//   - Нет HttpStatusEntryPoint (401 для неаутентифицированных — как в support-service)
//   - Только JWT фильтр + всё требует аутентификации кроме actuator
// Все пользователи имеют одинаковый доступ — notification-service не делает разграничение по ролям
//   (каждый аутентифицированный пользователь видит только СВОИ уведомления — проверка в сервисе).
public class SecurityConfig {

    // JwtAuthFilter — наш фильтр JWT аутентификации.
    private final JwtAuthFilter jwtAuthFilter;

    // securityFilterChain — главный @Bean Spring Security настроек.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF отключён: REST API с Bearer токенами не нуждается в CSRF защите.
                .csrf(AbstractHttpConfigurer::disable)

                // STATELESS: нет HTTP сессий, каждый запрос аутентифицируется через JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Правила авторизации:
                .authorizeHttpRequests(auth -> auth
                        // /actuator/** — открыто для мониторинга (Docker healthcheck, Grafana).
                        .requestMatchers("/actuator/**").permitAll()
                        // Всё остальное требует аутентификации.
                        // При отсутствии токена: Spring Security 6 вернёт 403 (нет HttpStatusEntryPoint).
                        // Нет @PreAuthorize — роли не проверяются на уровне эндпоинтов.
                        .anyRequest().authenticated()
                )
                // JwtAuthFilter ДО UsernamePasswordAuthenticationFilter:
                // устанавливает SecurityContext до того как Spring проверяет авторизацию.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
