package com.cinema.support.exception;

import com.cinema.dto.common.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

// @Slf4j — Lombok: Logger для логирования ошибок.
@Slf4j
// @RestControllerAdvice — перехватывает исключения из всех @RestController в сервисе.
// Комбинирует @ControllerAdvice + @ResponseBody:
//   - @ControllerAdvice: глобальный обработчик исключений
//   - @ResponseBody: ответы сериализуются в JSON (не HTML страница ошибки)
// Без этого класса Spring возвращал бы стандартную страницу ошибки или HTTP 500.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // handleResourceNotFoundException — обрабатывает "объект не найден".
    // Возвращает HTTP 404 с JSON телом ErrorResponse.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("RESOURCE_NOT_FOUND")  // машиночитаемый код ошибки
                .message(ex.getMessage())           // человекочитаемое сообщение (напр. "Support ticket not found: 999")
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);  // HTTP 404
    }

    // handleAccessDeniedException — обрабатывает com.cinema.support.exception.AccessDeniedException.
    // Это бизнес-исключение: клиент пытается обратиться к чужому тикету.
    // Возвращает HTTP 403 Forbidden.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message(ex.getMessage())           // "You do not have access to this ticket"
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);  // HTTP 403
    }

    // handleIllegalArgumentException — некорректные аргументы в запросе.
    // Возвращает HTTP 400 Bad Request.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("BAD_REQUEST")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);  // HTTP 400
    }

    // handleValidationException — нарушение @Valid/@NotBlank/@NotNull на полях DTO.
    // Spring бросает MethodArgumentNotValidException при провале Bean Validation.
    // Собираем все ошибки в одну строку: "field1: message1; field2: message2".
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        // getBindingResult().getFieldErrors() — список FieldError объектов
        // fe.getField() — имя поля ("subject", "content")
        // fe.getDefaultMessage() — сообщение аннотации ("must not be blank")
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);  // HTTP 400
    }

    // handleSpringSecurityAccessDenied — КРИТИЧЕСКИ ВАЖНЫЙ обработчик.
    // Перехватывает org.springframework.security.access.AccessDeniedException —
    //   это исключение Spring Security бросает когда @PreAuthorize("hasAuthority('ADMIN')")
    //   не выполняется (пользователь аутентифицирован, но не имеет нужной роли).
    //
    // БЕЗ ЭТОГО ОБРАБОТЧИКА: Spring Security бросает исключение → Spring MVC
    //   не знает как обработать (это не наш бизнес-класс) → HTTP 500 Internal Server Error!
    //
    // С ЭТИМ ОБРАБОТЧИКОМ: перехватываем → HTTP 403 Forbidden (корректный ответ).
    //
    // Полное имя класса в @ExceptionHandler — обязательно, потому что в этом пакете
    //   уже есть com.cinema.support.exception.AccessDeniedException с тем же простым именем.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSpringSecurityAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        log.warn("Spring Security access denied: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message("Access denied")  // общее сообщение (не раскрываем детали роли)
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);  // HTTP 403
    }

    // handleGenericException — запасной обработчик для всех непредвиденных исключений.
    // Перехватывает Exception (суперкласс всех checked исключений).
    // Возвращает HTTP 500 с общим сообщением (без деталей внутренней ошибки).
    // log.error с ex — логирует полный stacktrace для отладки разработчиком.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex);  // stacktrace в лог
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later.")  // без деталей — безопасность
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);  // HTTP 500
    }
}
