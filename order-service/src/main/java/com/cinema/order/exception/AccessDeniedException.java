package com.cinema.order.exception;

// Кастомное исключение для запрета доступа на бизнес-уровне.
// Отличие от org.springframework.security.access.AccessDeniedException:
//   - Spring Security AccessDeniedException: Spring сам бросает при нарушении @PreAuthorize
//   - com.cinema.order.exception.AccessDeniedException (этот класс): бросаем вручную в OrderService
//     при проверке "является ли пользователь владельцем заказа" (order.getUserId().equals(userId))
// Оба типа обрабатываются в GlobalExceptionHandler → HTTP 403 Forbidden.
public class AccessDeniedException extends RuntimeException {

    // Конструктор с описательным сообщением: "You do not have access to this order"
    public AccessDeniedException(String message) {
        super(message);
    }
}
