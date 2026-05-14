package com.cinema.support.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;

// @Slf4j — Lombok генерирует: private static final Logger log = LoggerFactory.getLogger(JwtUtils.class)
// Используется для предупреждений при невалидных токенах.
@Slf4j
// @Component — Spring управляет жизненным циклом этого бина.
// JwtUtils инжектируется в JwtAuthFilter через @RequiredArgsConstructor.
@Component
// JwtUtils в support-service — ИДЕНТИЧЕН коду в order-service, hall-service, movie-service, auth-service.
// Каждый сервис имеет свою копию для независимости (микросервисная изоляция).
// Все сервисы используют ОДИН и тот же секретный ключ из переменной среды JWT_SECRET.
// Токен создаётся в auth-service и верифицируется в каждом сервисе самостоятельно.
public class JwtUtils {

    // @Value — Spring инжектирует значение из application.yml: jwt.secret
    // В продакшн: переменная JWT_SECRET из Docker/Kubernetes environment.
    // Значение по умолчанию: mySecretKey12345678901234567890123456789012345678901234567890
    // КРИТИЧНО: одинаковый секрет во всех сервисах — иначе токены не верифицируются.
    @Value("${jwt.secret}")
    private String jwtSecret;

    // getSigningKey — преобразует строку-секрет в объект SecretKey для HMAC-SHA256.
    // Keys.hmacShaKeyFor() требует минимум 256-битный ключ (32 байта).
    // Наш секрет содержит 60+ символов — достаточно.
    // SecretKey — javax.crypto интерфейс, конкретная реализация зависит от алгоритма.
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // validateToken — проверяет подпись и срок действия JWT токена.
    // Возвращает true только если токен валиден.
    // Обрабатывает все возможные исключения jjwt 0.12.5:
    public boolean validateToken(String token) {
        try {
            // Jwts.parser() — создаёт строитель парсера (jjwt 0.12.5 API).
            // .verifyWith(key) — задаёт ключ для проверки подписи (HMAC-SHA256).
            // .build() — создаёт неизменяемый парсер.
            // .parseSignedClaims(token) — парсит токен И проверяет подпись + срок действия.
            // Если токен валиден — возвращает JWS<Claims>, иначе бросает исключение.
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;  // токен прошёл все проверки
        } catch (ExpiredJwtException e) {
            // Срок действия токена истёк (exp claim < current time)
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            // Тип токена не поддерживается (например, неподписанный JWT)
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            // Токен имеет неверный формат (не 3 части разделённых точкой)
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            // Подпись токена не совпадает с ожидаемой (неверный секрет или подделка)
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            // Токен null, пустой или некорректный аргумент
            log.warn("JWT token compact of handler are invalid: {}", e.getMessage());
        }
        return false;  // любое исключение = невалидный токен
    }

    // getUserIdFromToken — извлекает userId из claim "sub" (subject).
    // Возвращает String (не Long!):
    //   - auth-service генерирует: .subject(String.valueOf(user.getId())) → "42"
    //   - support-service JwtAuthFilter устанавливает principal = String "42"
    //   - SupportController делает: Long.parseLong(authentication.getName()) → 42L
    // Вызывать только после validateToken() — если токен невалиден, бросит исключение.
    public String getUserIdFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()   // Claims — Map с полями токена
                .getSubject();  // claim "sub" — userId как String
    }

    // getRolesFromToken — извлекает список ролей из кастомного claim "roles".
    // auth-service записывает: .claim("roles", List.of(user.getRole().name()))
    // Пример: ["CLIENT"], ["ADMIN"], ["SELLER"]
    // @SuppressWarnings("unchecked") — подавляет предупреждение о непроверяемом касте
    //   (Object → List<String>). Кастование безопасно: мы сами контролируем формат токена.
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object roles = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("roles");   // кастомный claim, добавленный при генерации токена
        // Проверяем что roles — действительно List (защита от неожиданного формата)
        if (roles instanceof List<?>) {
            return (List<String>) roles;  // безопасный кастинг — List<String>
        }
        return List.of();  // пустой список если claim отсутствует или имеет неверный тип
    }
}
