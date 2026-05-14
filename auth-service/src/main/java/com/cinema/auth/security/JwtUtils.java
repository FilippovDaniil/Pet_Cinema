package com.cinema.auth.security; // Пакет безопасности auth-service

import com.cinema.auth.entity.User;
import io.jsonwebtoken.*;                        // Классы jjwt: Jwts, Claims, ExpiredJwtException и т.д.
import io.jsonwebtoken.security.Keys;            // Утилита для создания ключей из строки
import io.jsonwebtoken.security.SignatureException; // Исключение при неверной подписи
import lombok.extern.slf4j.Slf4j;               // @Slf4j: добавляет поле log = LoggerFactory.getLogger(...)
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtils {
    // ГЕНЕРИРУЕТ и ВАЛИДИРУЕТ JWT. Это единственный сервис, который СОЗДАЁТ токены.
    // Все остальные сервисы только валидируют (используют аналогичный код, но без generate-методов).

    @Value("${jwt.secret}")
    private String jwtSecret; // Секрет из application.yml (или env JWT_SECRET)

    @Value("${jwt.access-token-expiration}")
    private long accessExpiration; // TTL access-токена в миллисекундах (900000 = 15 мин)

    @Value("${jwt.refresh-token-expiration}")
    private long refreshExpiration; // TTL refresh-токена в миллисекундах (604800000 = 7 дней)

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes()); // Создаём HMAC-SHA256 ключ из строки-секрета
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpiration); // Время истечения = сейчас + 15 мин

        return Jwts.builder()
                .id(UUID.randomUUID().toString())       // jti (JWT ID) — уникальный ID токена
                                                         // Нужен т.к. iat/exp с точностью до секунды, а не мс
                .subject(String.valueOf(user.getId()))  // sub = ID пользователя (строка "42")
                .claim("roles", List.of(user.getRole().name())) // Кастомный claim: ["ROLE_ADMIN"]
                .issuedAt(now)                           // iat = время выдачи
                .expiration(expiryDate)                  // exp = время истечения
                .signWith(getSigningKey())               // Подписываем HMAC-SHA256
                .compact();                              // Собираем строку base64url.base64url.base64url
    }

    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration); // +7 дней

        return Jwts.builder()
                .id(UUID.randomUUID().toString())      // Уникальный ID
                .subject(String.valueOf(user.getId())) // sub = userId
                // Нет claim "roles" — refresh-токен не используется для авторизации, только для ротации
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey()) // Проверяем подпись
                    .build()
                    .parseSignedClaims(token);   // Парсим — если ошибка, бросает исключение
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage()); // Токен истёк
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage()); // Неподдерживаемый тип JWT
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage()); // Повреждённый токен
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage()); // Подпись не совпала
        } catch (IllegalArgumentException e) {
            log.warn("JWT token compact of handler are invalid: {}", e.getMessage()); // Пустая строка
        }
        return false;
    }

    public String getUserIdFromToken(String token) {
        // Извлекает sub (userId) из уже проверенного токена
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject(); // Возвращает строку, например "42"
    }

    @SuppressWarnings("unchecked") // Подавляем предупреждение о непроверенном касте List<?>
    public List<String> getRolesFromToken(String token) {
        // Извлекает claim "roles" из access-токена
        Object roles = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("roles"); // Получаем объект по ключу "roles"
        if (roles instanceof List<?>) {
            return (List<String>) roles; // Кастуем к List<String> — в токене всегда строки
        }
        return List.of(); // Если claim отсутствует (refresh-токен) — пустой список
    }
}
