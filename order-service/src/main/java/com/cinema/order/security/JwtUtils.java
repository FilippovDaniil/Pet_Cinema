package com.cinema.order.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;

// @Slf4j — Lombok генерирует поле: private static final Logger log = LoggerFactory.getLogger(JwtUtils.class)
@Slf4j
// @Component — Spring управляет этим бином, инжектируется в JwtAuthFilter
@Component
public class JwtUtils {

    // JWT секрет инжектируется из application.yml (jwt.secret) или переменной окружения JWT_SECRET.
    // Один и тот же секрет используется во всех сервисах — централизованная выдача токенов auth-service.
    @Value("${jwt.secret}")
    private String jwtSecret;

    // Создаёт ключ подписи HMAC-SHA256 из строки секрета.
    // Keys.hmacShaKeyFor() требует минимум 32 байта (256 бит) — проверяется при запуске.
    // Метод private — используется только внутри класса.
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // Проверяет подпись токена и срок действия.
    // Возвращает true если токен валиден, false если истёк, повреждён или с неверной подписью.
    // Все исключения jjwt перехватываются отдельно — для логирования причины невалидности.
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())  // устанавливаем ключ для верификации подписи
                    .build()
                    .parseSignedClaims(token);    // парсит + проверяет подпись + exp claim
            return true;
        } catch (ExpiredJwtException e) {
            // Токен истёк — нормальная ситуация (access token живёт 15 минут)
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            // Неподдерживаемый формат токена (например, не compact serialization)
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            // Неверная структура токена (не три части, разделённые точкой)
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            // Подпись не совпадает с ключом — возможная попытка подделки токена
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            // Пустой или null токен
            log.warn("JWT token compact of handler are invalid: {}", e.getMessage());
        }
        return false;
    }

    // Извлекает subject (sub) из payload — это userId (строка, например "42").
    // В order-service principal хранится как String (в отличие от hall-service где Long).
    // Контроллер затем делает Long.parseLong(authentication.getName()) когда нужен числовой ID.
    // Вызывается только после validateToken() — токен гарантированно валиден.
    public String getUserIdFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();           // sub claim = userId (строка)
    }

    // Извлекает кастомный claim "roles" из payload.
    // auth-service добавляет: .claim("roles", List.of("ROLE_CLIENT")) при генерации токена.
    // @SuppressWarnings("unchecked") — подавляет предупреждение об unchecked cast:
    //   Object → List<String> нельзя проверить в рантайме (type erasure), но мы знаем формат.
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object roles = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("roles");           // кастомный claim из payload
        // Защита от null: если claim отсутствует — возвращаем пустой список
        if (roles instanceof List<?>) {
            return (List<String>) roles;
        }
        return List.of();
    }
}
