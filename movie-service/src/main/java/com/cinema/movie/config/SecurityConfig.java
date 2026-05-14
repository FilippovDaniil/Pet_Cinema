package com.cinema.movie.config;

import com.cinema.movie.security.JwtAuthFilter;
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

// @Configuration — класс содержит определения бинов Spring (@Bean методы)
// @EnableWebSecurity — активирует Spring Security для этого приложения
// @EnableMethodSecurity — включает @PreAuthorize / @PostAuthorize на методах контроллеров и сервисов
// @RequiredArgsConstructor — Lombok: конструктор с jwtAuthFilter для DI
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter; // Наш фильтр для разбора JWT-токена из заголовка

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Отключаем CSRF (Cross-Site Request Forgery) защиту.
                // В REST API с JWT токенами CSRF не нужен: браузер не отправляет токен автоматически
                // (в отличие от cookies), поэтому атака CSRF невозможна.
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless: Spring Security не создаёт и не использует HTTP-сессии.
                // Аутентификация проверяется заново при каждом запросе через JWT.
                // Без STATELESS Spring мог бы создать сессию и аутентифицировать через cookie.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // GET /api/movies и GET /api/movies/{id} — публичные: афиша доступна без токена
                        .requestMatchers(HttpMethod.GET, "/api/movies/**").permitAll()
                        // GET /api/genres — публичный: список жанров для фильтра на главной странице
                        .requestMatchers(HttpMethod.GET, "/api/genres").permitAll()
                        // Swagger UI и Actuator — для разработки и мониторинга, без авторизации
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll()
                        // Всё остальное (POST/PUT/DELETE для фильмов, жанров, отзывов) — требует токен.
                        // Дополнительная проверка ролей — через @PreAuthorize в контроллерах.
                        .anyRequest().authenticated()
                )
                // Ставим наш JWT-фильтр ПЕРЕД стандартным UsernamePasswordAuthenticationFilter.
                // Порядок важен: наш фильтр должен прочитать токен и установить SecurityContext
                // до того, как Spring Security начнёт проверять авторизацию.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
