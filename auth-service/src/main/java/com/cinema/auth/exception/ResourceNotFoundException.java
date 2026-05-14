package com.cinema.auth.exception; // Пакет исключений auth-service

public class ResourceNotFoundException extends RuntimeException {
    // Кастомное исключение "сущность не найдена".
    // GlobalExceptionHandler вернёт HTTP 404 Not Found.

    public ResourceNotFoundException(String message) {
        super(message); // Простой вариант: ResourceNotFoundException("User not found")
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super(String.format("%s not found with %s: '%s'", resource, field, value));
        // Удобный вариант: ResourceNotFoundException("User", "id", 42)
        // → "User not found with id: '42'"
    }
}
