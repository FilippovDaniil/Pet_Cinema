package com.cinema.auth.filter; // Пакет фильтров auth-service

import com.cinema.auth.security.JwtUtils;
import jakarta.servlet.FilterChain;            // Цепочка фильтров Servlet
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // Объект аутентификации Spring Security
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;               // Хранилище аутентификации для текущего потока
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource; // Добавляет детали запроса в аутентификацию
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;   // Утилита для проверки строк (StringUtils.hasText)
import org.springframework.web.filter.OncePerRequestFilter; // Базовый класс — выполняется ровно 1 раз за запрос

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    // Servlet-фильтр (НЕ Gateway-фильтр!). Работает внутри auth-service.
    // Цель: разрешить запросы /api/auth/me и /api/auth/me PATCH с проверкой JWT.

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractBearerToken(request); // Извлекаем JWT из заголовка Authorization

            if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
                // Токен присутствует и валиден — аутентифицируем запрос
                String userId = jwtUtils.getUserIdFromToken(token); // Например "42"
                List<String> roles = jwtUtils.getRolesFromToken(token); // Например ["ROLE_CLIENT"]

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new) // "ROLE_CLIENT" → SimpleGrantedAuthority
                        .collect(Collectors.toList());

                // Создаём объект аутентификации: principal = userId (строка), credentials = null (нет пароля)
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // buildDetails() добавляет IP-адрес и session ID в объект аутентификации

                SecurityContextHolder.getContext().setAuthentication(authentication);
                // Помещаем аутентификацию в ThreadLocal-хранилище.
                // Теперь в контроллере authentication.getName() вернёт userId.
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage()); // Логируем, но не прерываем цепочку
        }

        filterChain.doFilter(request, response); // Передаём запрос следующему фильтру/контроллеру
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization"); // Читаем заголовок
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // "Bearer <token>" → "<token>"
        }
        return null; // Нет токена — метод вернёт null, фильтр пропустит аутентификацию
    }
}
