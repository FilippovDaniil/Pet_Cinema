package com.cinema.support.exception;

// AccessDeniedException — исключение для нарушения бизнес-логики доступа.
//
// ВАЖНО: это com.cinema.support.exception.AccessDeniedException —
//   ОТДЕЛЬНЫЙ класс от org.springframework.security.access.AccessDeniedException.
//
// Два разных сценария использования:
//   1. com.cinema.support.exception.AccessDeniedException (этот класс):
//      Бросается в SupportService.sendMessage() / getMessages() когда
//      CLIENT пытается обратиться к ЧУЖОМУ тикету.
//      Обрабатывается @ExceptionHandler(AccessDeniedException.class) → HTTP 403.
//
//   2. org.springframework.security.access.AccessDeniedException:
//      Бросается Spring Security когда @PreAuthorize("hasAuthority('ADMIN')") не выполняется.
//      Обрабатывается отдельным @ExceptionHandler в GlobalExceptionHandler → HTTP 403.
//      Без этого обработчика Spring вернул бы HTTP 500!
//
// extends RuntimeException — unchecked, не требует try/catch.
public class AccessDeniedException extends RuntimeException {

    // Конструктор с сообщением об ошибке.
    // Пример: "You do not have access to this ticket"
    public AccessDeniedException(String message) {
        super(message);
    }
}
