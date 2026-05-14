package com.cinema.movie.exception;

// Кастомное исключение — сигнализирует об отсутствии запрошенного ресурса в БД.
// Расширяет RuntimeException (unchecked) — не нужно объявлять в throws, Spring перехватывает.
// GlobalExceptionHandler ловит его и возвращает HTTP 404 с {"errorCode":"NOT_FOUND","message":"..."}.
public class ResourceNotFoundException extends RuntimeException {

    // Принимает описательное сообщение: "Movie not found with id: 42"
    // super(message) передаёт текст в RuntimeException → getMessage() вернёт его
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
