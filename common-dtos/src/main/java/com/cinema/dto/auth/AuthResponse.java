package com.cinema.dto.auth; // Пакет для DTO аутентификации

import lombok.AllArgsConstructor; // Lombok: конструктор со всеми полями
import lombok.Builder;            // Lombok: паттерн Builder
import lombok.Data;               // Lombok: геттеры, сеттеры, equals, hashCode, toString
import lombok.NoArgsConstructor;  // Lombok: пустой конструктор

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;  // JWT access-токен: короткоживущий (15 мин), передаётся в заголовке Authorization: Bearer <token>
    private String refreshToken; // JWT refresh-токен: долгоживущий (7 дней), хранится в БД + Redis, используется для получения нового access-токена
}
