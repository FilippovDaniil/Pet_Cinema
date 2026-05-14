package com.cinema.movie.exception;

import com.cinema.dto.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

// @RestControllerAdvice — глобальный обработчик исключений для всех @RestController в приложении.
// Перехватывает исключения ДО того, как Spring вернёт стандартный ответ (HTML или 500).
// Каждый @ExceptionHandler определяет: какое исключение → какой HTTP-статус + тело ответа.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ResourceNotFoundException (из сервисов) → HTTP 404 Not Found
    // Пример: GET /api/movies/999 — фильма с id=999 нет в БД
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("NOT_FOUND")       // Машиночитаемый код для фронтенда
                .message(ex.getMessage())      // "Movie not found with id: 999"
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // AlreadyExistsException (из сервисов) → HTTP 409 Conflict
    // Пример: POST /api/genres с именем, которое уже есть; или второй отзыв от того же пользователя
    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyExists(AlreadyExistsException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("CONFLICT")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // MethodArgumentNotValidException — бросается Spring при неудаче @Valid на @RequestBody.
    // Собираем ВСЕ ошибки валидации в одну строку через "; ".
    // Пример: "title: must not be blank; durationMinutes: must be greater than 0"
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage) // Берём сообщение из аннотации: @NotBlank(message="...")
                .collect(Collectors.joining("; ")); // Объединяем через точку с запятой
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // AccessDeniedException — Spring Security бросает при нарушении @PreAuthorize.
    // Например: CLIENT пытается вызвать POST /api/movies (только для ADMIN).
    // БЕЗ этого обработчика Spring Security 6 вернул бы HTTP 500 вместо 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("ACCESS_DENIED")
                .message("Access denied") // Намеренно не раскрываем детали (почему отказано)
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // Catch-all для всех прочих исключений (NullPointerException, IllegalArgumentException и т.д.)
    // Возвращает HTTP 500 с описанием — в продакшене лучше скрыть детали, но для учебного проекта ок.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INTERNAL_ERROR")
                .message("An unexpected error occurred: " + ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
