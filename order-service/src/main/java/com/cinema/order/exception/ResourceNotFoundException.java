package com.cinema.order.exception;

// Исключение для случаев когда запрошенный ресурс не найден в БД.
// Обрабатывается в GlobalExceptionHandler → HTTP 404 Not Found.
// extends RuntimeException — unchecked: не нужно объявлять throws в сигнатуре метода.
public class ResourceNotFoundException extends RuntimeException {

    // Конструктор принимает описание: "Order not found: 42", "Session not found: 7"
    public ResourceNotFoundException(String message) {
        super(message); // передаём в RuntimeException для getMessage()
    }
}
