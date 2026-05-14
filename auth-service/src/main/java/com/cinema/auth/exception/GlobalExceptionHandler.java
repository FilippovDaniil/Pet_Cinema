package com.cinema.auth.exception; // Пакет исключений auth-service

import com.cinema.dto.common.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException; // Исключение при провале @Valid валидации
import org.springframework.web.bind.MissingRequestHeaderException;  // Исключение если обязательный заголовок отсутствует
import org.springframework.web.bind.annotation.ExceptionHandler;    // Аннотация: метод обрабатывает конкретный тип исключения
import org.springframework.web.bind.annotation.RestControllerAdvice; // Глобальный обработчик для всех @RestController

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice // = @ControllerAdvice + @ResponseBody. Обрабатывает исключения из ВСЕХ контроллеров.
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class) // Перехватываем ResourceNotFoundException
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("RESOURCE_NOT_FOUND")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error); // HTTP 404
    }

    @ExceptionHandler(AuthException.class) // Перехватываем AuthException
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("AUTHENTICATION_ERROR")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error); // HTTP 401
    }

    @ExceptionHandler(IllegalArgumentException.class) // Например: "Username already taken"
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("BAD_REQUEST")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error); // HTTP 400
    }

    @ExceptionHandler(MissingRequestHeaderException.class) // Если обязательный заголовок не передан
    public ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        log.warn("Missing required header: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("BAD_REQUEST")
                .message("Required header '" + ex.getHeaderName() + "' is missing") // Говорим клиенту какой заголовок нужен
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class) // Срабатывает при @Valid — когда поля не прошли валидацию
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage()) // "username: must not be blank"
                .collect(Collectors.joining("; ")); // Объединяем все ошибки через "; "
        log.warn("Validation failed: {}", message);
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error); // HTTP 400
    }

    @ExceptionHandler(Exception.class) // Ловим ВСЕ остальные необработанные исключения
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", ex.getMessage(), ex); // ERROR уровень + полный стектрейс
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error); // HTTP 500
    }
}
