package com.cinema.order.security;

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

// @Slf4j — Lombok: генерирует поле log для логирования
@Slf4j
// @Component — Spring создаёт бин фильтра, SecurityConfig инжектирует его через @RequiredArgsConstructor
@Component
// @RequiredArgsConstructor — Lombok: конструктор для final поля jwtUtils (DI без @Autowired)
@RequiredArgsConstructor
// OncePerRequestFilter — гарантирует что doFilterInternal вызывается ровно один раз на HTTP запрос.
// Базовый класс Spring — безопасен при forwarding / включении (которые могут вызвать фильтр дважды).
public class JwtAuthFilter extends OncePerRequestFilter {

    // JwtUtils инжектируется конструктором (через @RequiredArgsConstructor)
    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Извлекаем токен из заголовка Authorization: Bearer <token>
            String token = extractBearerToken(request);

            // Аутентифицируем только если токен есть и валиден (подпись, срок действия)
            if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
                // Извлекаем userId как строку ("42").
                // ВАЖНО: в order-service principal = String, а не Long (в отличие от hall-service).
                // Контроллер делает Long.parseLong(authentication.getName()) когда нужен числовой ID.
                String userId = jwtUtils.getUserIdFromToken(token);

                // Извлекаем роли из claim "roles": ["ROLE_CLIENT"] → [SimpleGrantedAuthority("ROLE_CLIENT")]
                List<String> roles = jwtUtils.getRolesFromToken(token);

                // Конвертируем строковые роли в объекты Spring Security
                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                // Создаём объект аутентификации:
                //   principal = userId (String "42")
                //   credentials = null (пароль не нужен — токен уже проверен)
                //   authorities = список ролей
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                // Сохраняем детали запроса (IP, session) — полезно для логирования и аудита
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Устанавливаем аутентификацию в SecurityContext.
                // После этого @PreAuthorize и hasAuthority() работают корректно для этого запроса.
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // Если токен отсутствует или невалиден — SecurityContext остаётся пустым.
            // Spring Security применит правила доступа: для защищённых эндпоинтов вернёт 401.
        } catch (Exception e) {
            // Перехватываем любые непредвиденные ошибки (не блокируем цепочку фильтров)
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        // Передаём запрос следующему фильтру в цепочке (или сервлету если фильтры закончились)
        filterChain.doFilter(request, response);
    }

    // Вспомогательный метод: извлекает токен из заголовка Authorization.
    // Формат: "Bearer eyJhbGci..." → возвращает "eyJhbGci..."
    // Возвращает null если заголовок отсутствует или не начинается с "Bearer ".
    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        // StringUtils.hasText() — проверяет что строка не null, не пустая и не только пробелы
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // "Bearer " занимает 7 символов
        }
        return null;
    }
}
