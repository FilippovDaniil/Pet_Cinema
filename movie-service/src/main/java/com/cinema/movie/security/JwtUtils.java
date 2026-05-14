package com.cinema.movie.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

// JwtUtils в movie-service — только ВАЛИДАЦИЯ и ЧТЕНИЕ токенов, генерации нет.
// Токены создаются в auth-service. Все сервисы используют один и тот же секретный ключ (JWT_SECRET),
// поэтому могут самостоятельно проверить подпись без обращения к auth-service.
// Код идентичен во всех сервисах (намеренное дублирование ради изоляции сервисов).
@Component
public class JwtUtils {

    @Value("${jwt.secret}") // Загружается из application.yml: jwt.secret (или env-переменная JWT_SECRET)
    private String jwtSecret;

    // Преобразует строку секрета в SecretKey для HMAC-SHA алгоритма.
    // Keys.hmacShaKeyFor() требует минимум 256 бит (32 байта). Наш секрет — 60 символов = безопасно.
    // StandardCharsets.UTF_8: явное указание кодировки — защита от различий платформ.
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // Проверяет токен: подпись + срок действия.
    // Все исключения jjwt наследуют JwtException — ловим базовым Exception для простоты.
    // Примеры исключений: ExpiredJwtException, MalformedJwtException, SignatureException.
    // Возвращает false (а не бросает) — вызывающий код (JwtAuthFilter) сам решает что делать.
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey()) // Устанавливает ключ для проверки подписи
                    .build()
                    .parseSignedClaims(token);   // Парсит и проверяет подпись + exp (срок истечения)
            return true;
        } catch (Exception e) {
            return false; // Невалидный, истёкший или подделанный токен
        }
    }

    // Извлекает userId из claim "sub" (subject).
    // В auth-service: Jwts.builder().subject(String.valueOf(user.getId()))...
    // Поэтому sub = "42" (строка), конвертируем обратно в Long.
    public Long extractUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    // Извлекает список ролей из custom claim "roles".
    // В auth-service: .claim("roles", List.of("ROLE_CLIENT"))...
    // После десериализации JSON-массив приходит как List<?>.
    // @SuppressWarnings("unchecked") подавляет предупреждение компилятора о непроверяемом касте.
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = parseClaims(token);
        Object rolesObj = claims.get("roles"); // Читаем custom claim "roles"
        if (rolesObj instanceof List<?>) {     // Защита от null и неожиданного типа
            return (List<String>) rolesObj;
        }
        return List.of(); // Если roles отсутствует — пустой список (refresh-token не имеет ролей)
    }

    // Внутренний метод: парсит токен и возвращает тело (payload) — объект Claims.
    // Claims содержит: sub (subject/userId), exp (expiration), iat (issued at), roles и другие поля.
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload(); // getPayload() = тело JWT (часть между двумя точками в base64)
    }
}
