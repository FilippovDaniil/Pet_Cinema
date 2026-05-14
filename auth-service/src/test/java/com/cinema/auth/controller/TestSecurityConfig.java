package com.cinema.auth.controller;

import org.springframework.boot.test.context.TestConfiguration; // Конфигурация только для тестов (не попадает в прод)
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

// Минимальная конфигурация Spring Security для @WebMvcTest тестов.
// Разрешает ВСЕ запросы — тесты проверяют только логику контроллера, а не авторизацию.
// Без этого класса @WebMvcTest использует SecurityConfig из прода и блокирует тестовые запросы.
@TestConfiguration
class TestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)         // Отключаем CSRF (не нужен в тестах)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); // Разрешаем всё
        return http.build();
    }
}
