package com.cinema.support.security;

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

// @Slf4j — Lombok создаёт Logger для логирования ошибок аутентификации.
@Slf4j
// @Component — Spring регистрирует этот фильтр как бин.
// SecurityConfig добавляет его в цепочку через: .addFilterBefore(jwtAuthFilter, ...)
@Component
// @RequiredArgsConstructor — Lombok генерирует конструктор для final поля jwtUtils.
// Spring автоматически инжектирует JwtUtils бин через этот конструктор.
@RequiredArgsConstructor
// OncePerRequestFilter — абстрактный класс Spring: гарантирует что фильтр вызывается
// РОВНО ОДИН РАЗ на каждый HTTP запрос (защита от двойной аутентификации при forward/include).
public class JwtAuthFilter extends OncePerRequestFilter {

    // jwtUtils — используется для валидации токена и извлечения userId/ролей.
    // final + @RequiredArgsConstructor = конструкторная инжекция (рекомендуемый стиль Spring).
    private final JwtUtils jwtUtils;

    // doFilterInternal — главный метод фильтра.
    // Вызывается для каждого HTTP запроса ОДИН РАЗ.
    // Задача: если запрос содержит валидный Bearer токен — установить SecurityContext.
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Шаг 1: извлекаем токен из заголовка Authorization: Bearer <token>
            String token = extractBearerToken(request);

            // Шаг 2: если токен есть и он валиден — аутентифицируем пользователя
            // StringUtils.hasText() — проверяет что token != null && !token.isEmpty() && !token.isBlank()
            if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {

                // getUserIdFromToken возвращает String (например "42") — НЕ Long.
                // Это важно для SupportController: authentication.getName() → "42"
                // → Long.parseLong("42") → 42L.
                String userId = jwtUtils.getUserIdFromToken(token);

                // Извлекаем роли из claim "roles": ["CLIENT"] или ["ADMIN"] или ["SELLER"]
                List<String> roles = jwtUtils.getRolesFromToken(token);

                // Конвертируем строки ролей в объекты Spring Security.
                // SimpleGrantedAuthority("CLIENT") — Spring Security проверяет
                // через hasAuthority("CLIENT") или hasAuthority("ADMIN").
                // Обратите внимание: роли здесь "CLIENT", "ADMIN", "SELLER" (без "ROLE_" префикса).
                // SupportController использует @PreAuthorize("hasAuthority('ADMIN')") — совпадает.
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                // Создаём объект аутентификации.
                // Параметры: principal=userId(String), credentials=null, authorities=список ролей.
                // Credentials=null потому что токен уже проверен — пароль больше не нужен.
                // Principal = String "42" (userId как строка) — SupportController делает getName() → "42"
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                // setDetails — добавляет детали запроса (IP адрес, session id).
                // Используется Spring Security для аудита и логирования.
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Устанавливаем аутентификацию в SecurityContext для текущего потока.
                // ThreadLocal хранилище — изолировано для каждого запроса.
                // После этого Spring Security видит пользователя как аутентифицированного.
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // Если токен отсутствует или невалиден — SecurityContext остаётся пустым.
            // SecurityConfig.anyRequest().authenticated() вернёт 403 для защищённых endpoints.
            // (В support-service нет HttpStatusEntryPoint → 403, не 401 как в order-service)

        } catch (Exception e) {
            // Любая ошибка при обработке токена логируется, но не прерывает цепочку фильтров.
            // Запрос продолжает обработку — Spring Security сам вернёт 401/403.
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        // ОБЯЗАТЕЛЬНО: передаём запрос следующему фильтру в цепочке.
        // Без этого вызова HTTP ответ никогда не придёт клиенту.
        filterChain.doFilter(request, response);
    }

    // extractBearerToken — извлекает JWT из заголовка Authorization.
    // Формат: "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
    // Если заголовок отсутствует или не начинается с "Bearer " — возвращает null.
    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        // startsWith("Bearer ") — проверяем именно "Bearer " (с пробелом после)
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);  // обрезаем первые 7 символов ("Bearer ")
        }
        return null;  // нет токена — вернём null, фильтр пропустит аутентификацию
    }
}
