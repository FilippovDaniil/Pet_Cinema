package com.cinema.hall.exception;

import com.cinema.dto.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

// @RestControllerAdvice — перехватчик исключений для ВСЕХ @RestController в приложении.
// Комбинация @ControllerAdvice + @ResponseBody:
//   @ControllerAdvice — применяет класс как глобальный обработчик для всех контроллеров
//   @ResponseBody     — ответ возвращается как JSON (не как HTML)
//
// Паттерн: сервисный слой кидает исключение → Spring ищет @ExceptionHandler → возвращает JSON.
// Без этого Spring вернул бы стандартный Whitelabel Error Page (HTML) или 500 Internal Server Error.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Обрабатывает ResourceNotFoundException → HTTP 404 Not Found.
    // Создаёт ErrorResponse (из common-dtos) с кодом "NOT_FOUND" и сообщением исключения.
    // Пример ответа: {"errorCode": "NOT_FOUND", "message": "Hall not found with id: 42"}
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("NOT_FOUND")
                .message(ex.getMessage()) // Сообщение из ResourceNotFoundException("Hall not found with id: 42")
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error); // 404
    }

    // Обрабатывает ошибки валидации Bean Validation (@Valid на @RequestBody).
    // MethodArgumentNotValidException содержит список FieldError (одно на каждое нарушенное правило).
    //
    // Пример: HallCreateRequest.name = "" (нарушает @NotBlank) → FieldError("name", "must not be blank")
    //
    // Собираем все сообщения через join("; ") — удобно при нескольких одновременных ошибках.
    // Пример ответа: {"errorCode": "VALIDATION_ERROR", "message": "name: must not be blank; rowsCount: must be positive"}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage) // Берём сообщение из аннотации валидации
                .collect(Collectors.joining("; "));  // Объединяем несколько ошибок через "; "
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error); // 400
    }

    // Fallback-обработчик для всех остальных исключений → HTTP 500 Internal Server Error.
    // Ловит NPE, IllegalArgumentException, DataAccessException и любые другие неожиданные ошибки.
    //
    // Примечание: hall-service не использует @PreAuthorize с сложной логикой,
    // поэтому AccessDeniedException не обрабатывается отдельно (Spring Security сам вернёт 403).
    // В более сложных сервисах (order-service, support-service) AccessDeniedException
    // обрабатывается явно.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INTERNAL_ERROR")
                .message("An unexpected error occurred: " + ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error); // 500
    }
}
