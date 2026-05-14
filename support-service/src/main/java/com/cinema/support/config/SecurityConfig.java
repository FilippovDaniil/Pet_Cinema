package com.cinema.support.config;

import com.cinema.support.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// @Configuration — говорит Spring что этот класс содержит @Bean определения.
// Spring обрабатывает его в специальном CGLIB proxy режиме.
@Configuration
// @EnableWebSecurity — включает Spring Security web интеграцию.
// Регистрирует SecurityFilterChain как фильтр в стандартной цепочке сервлетов.
@EnableWebSecurity
// @EnableMethodSecurity — включает аннотации безопасности на уровне методов.
// Активирует: @PreAuthorize, @PostAuthorize, @Secured, @RolesAllowed.
// ВАЖНО: без этой аннотации @PreAuthorize("hasAuthority('ADMIN')") в SupportController
//   просто игнорируется — Spring не проверяет условия доступа!
// prePostEnabled=true по умолчанию — именно это нужно для hasAuthority().
@EnableMethodSecurity
// @RequiredArgsConstructor — конструктор для final поля jwtAuthFilter.
// Spring инжектирует JwtAuthFilter бин автоматически.
@RequiredArgsConstructor
public class SecurityConfig {

    // JwtAuthFilter — наш кастомный фильтр аутентификации по JWT токену.
    // Добавляется в цепочку фильтров перед UsernamePasswordAuthenticationFilter.
    private final JwtAuthFilter jwtAuthFilter;

    // securityFilterChain — главный бин Spring Security.
    // Настраивает все правила безопасности: CSRF, сессии, авторизация, фильтры.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF (Cross-Site Request Forgery) защита ОТКЛЮЧЕНА.
                // CSRF защита нужна только для браузерных приложений с cookie-сессиями.
                // Наш API использует Bearer токены в Authorization заголовке — CSRF неприменим.
                // Если CSRF включён — тесты @WebMvcTest с POST запросами вернут 403 без csrf() токена!
                // (В SupportControllerTest используется .with(csrf()) — для совместимости с тестами)
                .csrf(AbstractHttpConfigurer::disable)

                // Сессии STATELESS — Spring Security НЕ создаёт HTTP сессии (куки не нужны).
                // Каждый запрос аутентифицируется через JWT токен заново.
                // SecurityContext сбрасывается после каждого запроса.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Правила авторизации запросов:
                .authorizeHttpRequests(auth -> auth
                        // /actuator/** — Actuator эндпоинты (health, metrics, info).
                        // Доступны без токена для мониторинга (Docker healthcheck, Grafana).
                        .requestMatchers("/actuator/**").permitAll()
                        // Все остальные запросы требуют аутентификации (валидного JWT).
                        // Если токен отсутствует или невалиден — Spring Security вернёт:
                        //   - 403 Forbidden (НЕ 401!) — потому что НЕТ HttpStatusEntryPoint.
                        //     В order-service добавлен HttpStatusEntryPoint для 401.
                        //     В support-service этого нет — стандартное поведение Spring Security 6.
                        // Дополнительные проверки ролей (@PreAuthorize("hasAuthority('ADMIN')"))
                        //   обрабатываются Spring Method Security ПОСЛЕ этого правила.
                        .anyRequest().authenticated()
                )
                // Регистрируем JwtAuthFilter ПЕРЕД стандартным UsernamePasswordAuthenticationFilter.
                // Порядок важен: JwtAuthFilter должен заполнить SecurityContext ДО того,
                //   как Spring Security проверяет авторизацию.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
