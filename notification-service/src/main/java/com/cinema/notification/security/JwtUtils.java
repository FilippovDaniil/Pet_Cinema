package com.cinema.notification.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;

// JwtUtils в notification-service — ИДЕНТИЧЕН коду во всех других сервисах.
// Каждый сервис самостоятельно верифицирует JWT токен с одним и тем же секретом.
// Комментарии идентичны support-service/JwtUtils.java.
@Slf4j
@Component
public class JwtUtils {

    // Секретный ключ из application.yml: jwt.secret.
    // Одинаковый во всех сервисах — иначе токены не верифицируются.
    @Value("${jwt.secret}")
    private String jwtSecret;

    // getSigningKey — преобразует строку в SecretKey для HMAC-SHA256 верификации.
    // Keys.hmacShaKeyFor() требует >= 32 байта (256 бит).
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    // validateToken — возвращает true если токен валиден (подпись и срок действия).
    // Перехватывает все jjwt исключения и логирует предупреждение.
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);  // проверяет подпись + exp claim
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token compact of handler are invalid: {}", e.getMessage());
        }
        return false;
    }

    // getUserIdFromToken — извлекает userId из claim "sub" как String.
    // Возвращает String "42", не Long 42L.
    // JwtAuthFilter устанавливает principal = String userId.
    // NotificationController кастит: (String) authentication.getPrincipal() → Long.parseLong().
    public String getUserIdFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();   // claim "sub" = userId как String
    }

    // getRolesFromToken — извлекает список ролей из кастомного claim "roles".
    // Возвращает List<String> например ["CLIENT"] или ["ADMIN"].
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object roles = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("roles");
        if (roles instanceof List<?>) {
            return (List<String>) roles;
        }
        return List.of();
    }
}
