package com.cinema.notification.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

// @Slf4j — Lombok: Logger для предупреждений и ошибок.
@Slf4j
// @RestControllerAdvice — глобальный обработчик исключений из всех @RestController.
// Комбинирует @ControllerAdvice (перехват) + @ResponseBody (JSON ответы).
@RestControllerAdvice
// GlobalExceptionHandler в notification-service отличается от других сервисов:
//   1. Использует стандартные Java/JPA исключения (не кастомные ResourceNotFoundException).
//      EntityNotFoundException (jakarta.persistence) вместо ResourceNotFoundException.
//      SecurityException (java.lang) вместо кастомного AccessDeniedException.
//   2. Ответ — Map<String, Object> с полями timestamp/status/error/message,
//      а не ErrorResponse из common-dtos (для разнообразия паттернов).
public class GlobalExceptionHandler {

    // handleNotFound — jakarta.persistence.EntityNotFoundException → HTTP 404.
    // Бросается в NotificationService.markAsRead() когда уведомление не найдено:
    //   orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id))
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // handleSecurity — java.lang.SecurityException → HTTP 403.
    // Бросается в NotificationService.markAsRead() когда userId не совпадает с владельцем:
    //   throw new SecurityException("Access denied to notification id=" + id)
    // Это java.lang.SecurityException (не Spring Security AccessDeniedException).
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // handleGeneral — запасной обработчик для любых других исключений → HTTP 500.
    // log.error с ex — логирует полный stacktrace.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    // buildResponse — вспомогательный метод для создания JSON ответа ошибки.
    // Возвращает Map<String, Object> вместо ErrorResponse:
    //   - Не требует зависимости от common-dtos для обработчика ошибок
    //   - Map.of() — иммутабельная Map (Java 9+): ключ → значение
    //   Поля:
    //     timestamp — текущее время в ISO формате (LocalDateTime.now().toString())
    //     status    — HTTP код (404, 403, 500) как число
    //     error     — HTTP reason phrase ("Not Found", "Forbidden")
    //     message   — конкретное сообщение об ошибке
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().toString(),   // "2024-01-15T10:30:00.123"
                "status", status.value(),                       // 404, 403, 500
                "error", status.getReasonPhrase(),             // "Not Found", "Forbidden"
                "message", message                             // детали ошибки
        );
        return ResponseEntity.status(status).body(body);
    }
}
