package com.cinema.auth.exception; // Пакет исключений auth-service

public class AuthException extends RuntimeException {
    // Кастомное исключение для ошибок аутентификации/авторизации.
    // RuntimeException — не нужно объявлять в throws (unchecked exception).
    // GlobalExceptionHandler перехватит его и вернёт HTTP 401 Unauthorized.

    public AuthException(String message) {
        super(message); // Передаём сообщение в базовый RuntimeException
    }
}
