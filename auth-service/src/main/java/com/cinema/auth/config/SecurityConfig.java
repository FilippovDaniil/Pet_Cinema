package com.cinema.auth.config; // Пакет конфигураций auth-service

import com.cinema.auth.filter.JwtAuthFilter;
import com.cinema.auth.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;                     // Менеджер аутентификации
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;            // Provider: сравнивает пароли через UserDetailsService
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration; // Доступ к AuthenticationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Для csrf.disable()
import org.springframework.security.config.http.SessionCreationPolicy;                       // Политика сессий
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // Стандартный фильтр логина

@Configuration
@EnableWebSecurity // Включает Spring Security и регистрирует SecurityFilterChain
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService; // Наша реализация загрузки пользователя по логину
    private final JwtAuthFilter jwtAuthFilter;               // Наш JWT-фильтр

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
        // BCrypt — безопасный алгоритм хеширования паролей с солью.
        // BCryptPasswordEncoder().encode("password") → "$2a$10$randomSalt..."
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService); // Откуда загружать пользователя
        provider.setPasswordEncoder(passwordEncoder());     // Как проверять пароль (BCrypt)
        return provider;
        // При логине: authenticationManager.authenticate(username+password)
        // → provider загружает User из БД → сравнивает BCrypt-хеши
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
        // Получаем AuthenticationManager из Spring Security конфигурации.
        // Используется в AuthService.login() для проверки credentials
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // Отключаем CSRF — не нужен для REST API с JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // STATELESS = не создавать HTTP-сессии. Каждый запрос независим (JWT несёт всю информацию).
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register", // Регистрация — без токена
                                "/api/auth/login",    // Логин — без токена
                                "/api/auth/refresh",  // Обновление токенов — без токена
                                "/api/auth/logout"    // Выход — без токена (токен передаётся в теле)
                        ).permitAll() // Эти пути разрешены всем (без аутентификации)
                        .anyRequest().authenticated() // Все остальные запросы — только с токеном
                )
                .authenticationProvider(authenticationProvider()) // Регистрируем наш DaoProvider
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
                // Вставляем JwtAuthFilter ПЕРЕД стандартным фильтром логина по форме
                // Порядок важен: наш фильтр идёт первым и устанавливает аутентификацию из JWT

        return http.build();
    }
}
