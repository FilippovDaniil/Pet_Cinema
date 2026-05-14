package com.cinema.hall.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

// @Component — Spring создаёт один экземпляр (Singleton) и управляет его жизненным циклом.
// JwtUtils внедряется в JwtAuthFilter через конструктор (благодаря @RequiredArgsConstructor в фильтре).
@Component
public class JwtUtils {

    // @Value — инжектирует значение из application.yml: jwt.secret
    // В Docker используется переменная среды JWT_SECRET (задаётся в docker-compose.yml).
    // Все микросервисы используют один и тот же секрет — необходимо для валидации токенов,
    // выданных auth-service, в каждом сервисе независимо.
    @Value("${jwt.secret}")
    private String jwtSecret;

    // Создаёт криптографический ключ для HMAC-SHA256 подписи/проверки JWT.
    // Keys.hmacShaKeyFor() принимает байты секрета (минимум 32 байта = 256 бит для SHA-256).
    // StandardCharsets.UTF_8 — явная кодировка (getBytes() без аргумента использует системную кодировку,
    // которая может отличаться на разных платформах).
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // Проверяет подпись и срок действия JWT токена.
    // Возвращает true если токен корректный, false если подделан или просрочен.
    // try-catch поглощает все исключения jjwt:
    //   ExpiredJwtException    — токен просрочен
    //   MalformedJwtException  — некорректный формат
    //   SignatureException     — подпись не совпадает (токен изменён)
    //   UnsupportedJwtException — неподдерживаемый тип JWT
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey()) // Устанавливаем ключ для проверки подписи
                    .build()
                    .parseSignedClaims(token);   // Парсит + проверяет подпись + проверяет exp
            return true;
        } catch (Exception e) {
            return false; // Любая ошибка = невалидный токен
        }
    }

    // Извлекает userId из claim "sub" (Subject) JWT токена.
    // auth-service при создании токена ставит: .subject(String.valueOf(user.getId()))
    // Поэтому здесь парсим строку обратно в Long.
    public Long extractUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    // Извлекает список ролей из кастомного claim "roles" JWT токена.
    // auth-service добавляет: .claim("roles", List.of(user.getRole().name()))
    // Например: ["ROLE_ADMIN"] или ["ROLE_CLIENT"].
    //
    // @SuppressWarnings("unchecked") — подавляет предупреждение компилятора о непроверенном касте.
    // Claims.get("roles") возвращает Object; безопасно приводим к List<String>
    // (знаем формат, т.к. сами генерируем токен в auth-service).
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = parseClaims(token);
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?>) {
            return (List<String>) rolesObj; // Возвращаем список ролей
        }
        return List.of(); // Нет ролей — возвращаем пустой список (не null)
    }

    // Приватный вспомогательный метод: парсит JWT и возвращает payload (claims).
    // Вызывается из extractUserId() и extractRoles().
    // Если токен невалиден — кинет исключение (не перехватываем здесь, т.к. вызываем
    // только после validateToken() в JwtAuthFilter).
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())  // Ключ для проверки подписи
                .build()
                .parseSignedClaims(token)    // Парсит JWS (подписанный JWT)
                .getPayload();               // Возвращает тело (Claims): sub, exp, roles и т.д.
    }
}
