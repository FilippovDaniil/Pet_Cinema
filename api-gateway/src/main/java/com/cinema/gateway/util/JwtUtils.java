package com.cinema.gateway.util; // Утилиты API Gateway

import io.jsonwebtoken.Claims;                    // Claims — полезная нагрузка JWT (sub, roles, exp и т.д.)
import io.jsonwebtoken.Jwts;                      // Основной класс для работы с JWT (парсинг, создание)
import io.jsonwebtoken.security.Keys;             // Утилита для создания криптографических ключей из строки
import org.springframework.beans.factory.annotation.Value; // Инжектирует значение из application.yml
import org.springframework.stereotype.Component;           // Регистрирует класс как Spring Bean

import javax.crypto.SecretKey;                    // Секретный ключ для HMAC подписи
import java.nio.charset.StandardCharsets;         // UTF-8 кодировка

@Component // Spring создаст один экземпляр этого класса и будет его переиспользовать
public class JwtUtils {
    // Утилита для валидации JWT в API Gateway.
    // Важно: Gateway только ПРОВЕРЯЕТ токен (не создаёт). Тот же секрет, что у auth-service.

    @Value("${jwt.secret}") // Читает значение jwt.secret из application.yml (или из env JWT_SECRET)
    private String secret;

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8); // Преобразуем строку-секрет в байты
        return Keys.hmacShaKeyFor(keyBytes); // Создаём HMAC-SHA ключ из байтов (jjwt 0.12.5 API)
    }

    public boolean validateToken(String token) {
        // Возвращает true если токен валиден (подпись совпадает, не истёк срок)
        // Используется в JwtAuthenticationFilter перед проксированием запроса
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey()) // Указываем ключ для проверки подписи
                    .build()
                    .parseSignedClaims(token);   // Парсим токен — если невалиден, бросает исключение
            return true;
        } catch (Exception e) {
            return false; // JwtException, ExpiredJwtException, SignatureException → false
        }
    }

    public Claims getClaims(String token) {
        // Извлекает все claims из токена (sub=userId, roles=[ROLE_ADMIN], exp, iat, jti)
        // Вызывается только после validateToken() — иначе может бросить исключение
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload(); // getPayload() возвращает объект Claims с доступом к полям токена
    }
}
