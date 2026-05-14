package com.cinema.notification.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

// JwtAuthFilter — идентичен support-service JwtAuthFilter.
// Комментарии идентичны — различий между сервисами нет (один и тот же паттерн).
@Slf4j
@Component
@RequiredArgsConstructor
// OncePerRequestFilter — вызывается ровно один раз на каждый HTTP запрос.
public class JwtAuthFilter extends OncePerRequestFilter {

    // JwtUtils инжектируется через конструктор (final + @RequiredArgsConstructor).
    private final JwtUtils jwtUtils;

    // doFilterInternal — основной метод фильтра.
    // Задача: извлечь JWT из Authorization заголовка, верифицировать, установить SecurityContext.
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Шаг 1: извлекаем Bearer токен из заголовка
            String token = extractBearerToken(request);

            // Шаг 2: если токен присутствует и валиден — аутентифицируем
            if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
                // userId — String principal ("42"), не Long
                // NotificationController использует: (String) authentication.getPrincipal()
                String userId = jwtUtils.getUserIdFromToken(token);
                List<String> roles = jwtUtils.getRolesFromToken(token);

                // Конвертируем роли в Spring Security authorities
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                // Создаём объект аутентификации.
                // principal = userId (String) — установлен как String (не Long!).
                // NotificationController: (String) authentication.getPrincipal() → Long.parseLong()
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Устанавливаем в ThreadLocal SecurityContext — Spring Security видит пользователя аутентифицированным.
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // Если токен отсутствует/невалиден — SecurityContext пуст →
            //   SecurityConfig.anyRequest().authenticated() вернёт 403 (нет HttpStatusEntryPoint).

        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        // Обязательно передаём запрос следующему фильтру.
        filterChain.doFilter(request, response);
    }

    // extractBearerToken — извлекает JWT из "Authorization: Bearer <token>".
    // substring(7) — пропускаем первые 7 символов ("Bearer ").
    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
