package com.cinema.order.exception;

import com.cinema.dto.common.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

// @Slf4j — Lombok: генерирует поле log
@Slf4j
// @RestControllerAdvice — перехватывает исключения из всех @RestController.
// Позволяет централизованно обрабатывать ошибки и возвращать единый формат ErrorResponse.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // HTTP 404: ресурс не найден (заказ, сеанс, товар меню)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("RESOURCE_NOT_FOUND")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // HTTP 403: бизнес-уровень запрета доступа.
    // Бросается в OrderService.getOrderById() когда userId != order.userId и роль не privileged.
    // Это com.cinema.order.exception.AccessDeniedException (наш кастомный класс).
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // HTTP 400: некорректные аргументы (например, неверный enum в запросе)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("BAD_REQUEST")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // HTTP 400: ошибки Bean Validation (@Valid на @RequestBody).
    // BindingResult содержит список FieldError — собираем в одну строку через "; ".
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage()) // "sessionId: must not be null"
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // КРИТИЧЕСКИ ВАЖНЫЙ HANDLER для Spring Security 6 + @PreAuthorize:
    //
    // Проблема: без этого handler-а Spring Security бросает
    //   org.springframework.security.access.AccessDeniedException
    //   при нарушении @PreAuthorize (например CLIENT пытается вызвать SELLER endpoint).
    // Spring 6 не знает как обработать это исключение в @RestControllerAdvice по умолчанию
    // и возвращает HTTP 500 Internal Server Error вместо 403 Forbidden.
    //
    // Решение: явно перехватываем org.springframework.security.access.AccessDeniedException
    // (НЕ наш com.cinema.order.exception.AccessDeniedException — это разные классы!)
    // и возвращаем 403 с понятным телом ответа.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringSecurityAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Spring Security access denied: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message("Access denied")
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // HTTP 500: любые неожиданные исключения (catch-all).
    // Логируем полный стектрейс (третий аргумент ex) для отладки через Grafana/Loki.
    // Клиенту возвращаем общее сообщение — не раскрываем детали реализации.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
