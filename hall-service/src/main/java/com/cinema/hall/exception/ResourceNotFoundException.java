package com.cinema.hall.exception;

// Кастомное исключение — бросается когда запрошенный ресурс не найден в БД.
// Аналог 404 Not Found на уровне бизнес-логики сервисного слоя.
//
// RuntimeException (unchecked) — не нужно объявлять в throws, код чище.
// GlobalExceptionHandler перехватывает это исключение и возвращает HTTP 404.
public class ResourceNotFoundException extends RuntimeException {

    // Конструктор принимает сообщение об ошибке.
    // Передаём его в RuntimeException.super(message) через super().
    // Пример: new ResourceNotFoundException("Hall not found with id: 42")
    public ResourceNotFoundException(String message) {
        super(message); // Сохраняет сообщение в Throwable.detailMessage; доступно через getMessage()
    }
}
