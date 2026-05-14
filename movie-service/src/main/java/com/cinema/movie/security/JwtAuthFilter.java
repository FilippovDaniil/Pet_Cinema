package com.cinema.movie.security;

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

// OncePerRequestFilter гарантирует, что фильтр выполняется ровно один раз за запрос.
// Без этого класса Servlet-контейнер мог бы вызвать фильтр несколько раз (при forward/include).
// @RequiredArgsConstructor: Lombok генерирует конструктор с jwtUtils — Spring использует его для DI.
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils; // Внедряется через конструктор (Lombok @RequiredArgsConstructor)

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. Извлекаем токен из заголовка Authorization (если есть)
        String token = extractToken(request);

        // 2. Только если токен присутствует И валиден — устанавливаем аутентификацию.
        //    Если токена нет (публичный эндпоинт) — просто пропускаем запрос дальше.
        //    Если токен невалиден — тоже пропускаем: Spring Security сам вернёт 403/401.
        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            Long userId = jwtUtils.extractUserId(token);  // sub claim → Long userId
            List<String> roles = jwtUtils.extractRoles(token); // custom claim "roles"

            // Преобразуем строки ролей в объекты Spring Security
            // "ROLE_CLIENT" → new SimpleGrantedAuthority("ROLE_CLIENT")
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // UsernamePasswordAuthenticationToken(principal, credentials, authorities):
            //   principal   = userId (Long) — кто аутентифицирован; контроллер читает через authentication.getPrincipal()
            //   credentials = null  — пароль не нужен (токен уже проверен)
            //   authorities = роли  — используются для @PreAuthorize("hasAuthority('ROLE_ADMIN')")
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            // Добавляем детали запроса (IP, sessionId) — полезно для аудит-логов
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Помещаем аутентификацию в SecurityContext — теперь Spring Security "знает" кто этот пользователь.
            // SecurityContextHolder хранит в ThreadLocal — изолировано для каждого потока/запроса.
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 3. Передаём запрос следующему фильтру в цепочке (или контроллеру).
        //    Всегда вызываем doFilter, иначе запрос не дойдёт до контроллера.
        filterChain.doFilter(request, response);
    }

    // Читает заголовок "Authorization: Bearer <token>" и возвращает только токен.
    // substring(7) — пропускаем "Bearer " (7 символов включая пробел).
    // StringUtils.hasText() проверяет: не null, не пустая, не только пробелы.
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer eyJhbGci..." → "eyJhbGci..."
        }
        return null; // Заголовка нет или формат неправильный
    }
}
