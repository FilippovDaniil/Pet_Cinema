package com.cinema.hall.config;

import com.cinema.hall.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// @Configuration — класс является источником @Bean-определений для Spring Context.
@Configuration
// @EnableWebSecurity — активирует поддержку Spring Security для веб-приложения.
// В Spring Boot 3 это добавляет стандартные фильтры безопасности.
@EnableWebSecurity
// @EnableMethodSecurity — включает аннотации метода-уровня: @PreAuthorize, @PostAuthorize, @Secured.
// Без этой аннотации @PreAuthorize("hasAuthority('ROLE_ADMIN')") в контроллере НЕ РАБОТАЕТ.
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // JwtAuthFilter внедряется через конструктор (сгенерирован @RequiredArgsConstructor).
    private final JwtAuthFilter jwtAuthFilter;

    // @Bean — Spring регистрирует возвращаемый объект как управляемый бин.
    // SecurityFilterChain — основная точка конфигурации Spring Security.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Отключаем CSRF-защиту. Причина: REST API — stateless, без сессий/кук.
            // CSRF актуален только для форм в браузере (сессионная аутентификация).
            // JWT-аутентификация не подвержена CSRF (заголовок Authorization не отправляется браузером автоматически).
            .csrf(AbstractHttpConfigurer::disable)

            // STATELESS — Spring Security не создаёт HTTP-сессии (нет Set-Cookie: JSESSIONID).
            // Каждый запрос аутентифицируется заново по JWT-токену.
            // Это правило для микросервисной архитектуры: горизонтальное масштабирование без sticky sessions.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // GET /api/halls/** — публичный доступ: список залов и их услуги видны всем.
                // Например, клиент выбирает зал без авторизации.
                .requestMatchers(HttpMethod.GET, "/api/halls/**").permitAll()

                // GET /api/sessions/** — публичный доступ: расписание сеансов видно всем.
                // Клиент просматривает сеансы до авторизации.
                .requestMatchers(HttpMethod.GET, "/api/sessions/**").permitAll()

                // Swagger UI и OpenAPI документация — доступны без авторизации (для разработчиков).
                // Actuator — метрики и health check для Docker healthcheck и мониторинга.
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()

                // Все остальные запросы (POST/PUT/DELETE /api/halls, POST/PUT/DELETE /api/sessions)
                // требуют валидного JWT токена.
                // Дополнительная проверка роли ADMIN делается через @PreAuthorize в контроллерах.
                .anyRequest().authenticated()
            )

            // Добавляем наш JwtAuthFilter В ЦЕПОЧКУ ПЕРЕД стандартным UsernamePasswordAuthenticationFilter.
            // Стандартный фильтр проверяет логин/пароль из формы — нам это не нужно.
            // Наш фильтр проверяет JWT из заголовка Authorization.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build(); // Строим и возвращаем настроенную цепочку безопасности
    }
}
