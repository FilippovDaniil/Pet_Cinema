package com.cinema.order.config;

import com.cinema.order.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// @Configuration — класс содержит @Bean методы для Spring контекста
@Configuration
// @EnableWebSecurity — активирует конфигурацию Spring Security (заменяет устаревший WebSecurityConfigurerAdapter)
@EnableWebSecurity
// @EnableMethodSecurity — активирует поддержку @PreAuthorize / @PostAuthorize на методах сервисов и контроллеров.
// Без этой аннотации аннотации @PreAuthorize молча игнорируются (нет ошибки — просто не работают).
@EnableMethodSecurity
// @RequiredArgsConstructor — Lombok: конструктор для final поля jwtAuthFilter
@RequiredArgsConstructor
public class SecurityConfig {

    // JwtAuthFilter инжектируется из Spring контекста (@Component в JwtAuthFilter)
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Отключаем CSRF — не нужно для stateless REST API (нет session cookies).
                // CSRF атака работает только при cookie-based аутентификации.
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless сессия — Spring Security не создаёт HttpSession.
                // Каждый запрос аутентифицируется заново через JWT токен в заголовке.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Правила авторизации (проверяются сверху вниз, первое совпавшее применяется)
                .authorizeHttpRequests(auth -> auth
                        // Webhook от payment-simulator — публичный. Нет смысла требовать токен:
                        // payment-simulator сам не имеет JWT (он не аутентифицированный сервис).
                        .requestMatchers(HttpMethod.POST, "/api/orders/webhook/payment").permitAll()

                        // Меню кинотеатра — публичная информация, доступна без авторизации
                        .requestMatchers(HttpMethod.GET, "/api/food-menu").permitAll()

                        // Swagger UI и Actuator — для разработки и мониторинга
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()

                        // Все остальные запросы требуют аутентификации (наличие валидного JWT)
                        .anyRequest().authenticated()
                )

                // КРИТИЧЕСКИ ВАЖНАЯ НАСТРОЙКА для корректного поведения Spring Security 6:
                // По умолчанию Spring Security 6 возвращает 302 Redirect (на /login) или 403 Forbidden
                // для неаутентифицированных запросов к защищённым эндпоинтам.
                // HttpStatusEntryPoint(UNAUTHORIZED) изменяет поведение: неаутентифицированный запрос
                // получает HTTP 401 Unauthorized — что правильно для REST API.
                // Без этого: отсутствие токена → 403 (путаница с "нет прав").
                // С этим:    отсутствие токена → 401 (нет аутентификации).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                // Добавляем наш JWT фильтр перед стандартным UsernamePasswordAuthenticationFilter.
                // Spring Security применяет фильтры в порядке цепочки:
                //   JwtAuthFilter (наш) → UsernamePasswordAuthenticationFilter → ... → Controller
                // JwtAuthFilter устанавливает Authentication в SecurityContext ДО проверки прав.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
