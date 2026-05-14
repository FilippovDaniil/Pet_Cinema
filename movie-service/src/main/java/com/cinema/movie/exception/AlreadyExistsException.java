package com.cinema.movie.exception;

// Кастомное исключение — сигнализирует о конфликте: ресурс уже существует.
// Пример: попытка создать жанр с дублирующимся именем или добавить второй отзыв от того же пользователя.
// GlobalExceptionHandler ловит и возвращает HTTP 409 Conflict с {"errorCode":"CONFLICT","message":"..."}.
public class AlreadyExistsException extends RuntimeException {

    public AlreadyExistsException(String message) {
        super(message);
    }
}
