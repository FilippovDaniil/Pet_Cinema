package com.cinema.hall.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

// @Component — Spring создаёт и управляет фильтром. SecurityConfig явно добавляет его в цепочку.
@Component
// @RequiredArgsConstructor — Lombok генерирует конструктор с полем jwtUtils (final-поле = обязательная зависимость).
// Spring внедряет JwtUtils через конструктор (constructor injection — предпочтительный способ).
@RequiredArgsConstructor
// OncePerRequestFilter гарантирует, что doFilterInternal() выполняется РОВНО ОДИН РАЗ на HTTP-запрос,
// даже если один запрос проходит через несколько сервлетов (например, при forward/include).
// Это безопаснее, чем GenericFilterBean, который может выполниться несколько раз.
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils; // Утилита для работы с JWT (валидация, извлечение данных)

    // Основной метод фильтра — выполняется для каждого HTTP-запроса.
    // Аргументы:
    //   request     — входящий HTTP-запрос
    //   response    — исходящий HTTP-ответ
    //   filterChain — цепочка фильтров; вызов chain.doFilter() передаёт запрос дальше
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Извлекаем JWT из заголовка Authorization
        String token = extractToken(request);

        // Если токен есть И он валиден (подпись + срок действия) — аутентифицируем пользователя
        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            Long userId = jwtUtils.extractUserId(token); // ID пользователя из claim "sub"
            List<String> roles = jwtUtils.extractRoles(token); // Роли из claim "roles"

            // Преобразуем строковые роли в GrantedAuthority — объекты Spring Security.
            // SimpleGrantedAuthority("ROLE_ADMIN") → авторитет "ROLE_ADMIN".
            // Именно эти авторитеты проверяет @PreAuthorize("hasAuthority('ROLE_ADMIN')").
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // UsernamePasswordAuthenticationToken — стандартный объект аутентификации Spring Security.
            // Параметры конструктора:
            //   principal   = userId (Long) — кто аутентифицирован
            //   credentials = null — пароль не нужен после валидации JWT
            //   authorities — список ролей/прав
            // Три аргумента = аутентифицированный токен (isAuthenticated() = true).
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            // setDetails() добавляет IP-адрес и сессию запроса в объект аутентификации.
            // Полезно для аудита (логирование, SecurityAuditEvent).
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Помещаем аутентификацию в SecurityContext.
            // SecurityContextHolder использует ThreadLocal — каждый поток (запрос) имеет свой контекст.
            // После этого Spring Security знает: "запрос выполняет пользователь с id=X и ролью Y".
            // Контроллер может получить userId: (Long) authentication.getPrincipal()
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        // Если токена нет или он невалиден — SecurityContext остаётся пустым.
        // Spring Security применит правила SecurityConfig:
        //   - permitAll() эндпоинты — пройдут без аутентификации
        //   - authenticated() эндпоинты — вернут 401/403

        // Обязательно передаём запрос дальше по цепочке фильтров.
        // Без этого запрос "застрянет" в фильтре и никогда не достигнет контроллера.
        filterChain.doFilter(request, response);
    }

    // Извлекает токен из заголовка "Authorization: Bearer <token>".
    // Формат Bearer Token определён в RFC 6750.
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization"); // Получаем заголовок Authorization
        // StringUtils.hasText() — проверяет, что строка не null, не пустая и не пробельная.
        // bearerToken.startsWith("Bearer ") — убеждаемся, что это Bearer-токен, а не Basic Auth.
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // Обрезаем "Bearer " (7 символов) → возвращаем сам JWT
        }
        return null; // Токена нет — вернём null, doFilterInternal пропустит аутентификацию
    }
}
