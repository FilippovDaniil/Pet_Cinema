package com.cinema.support.exception;

// ResourceNotFoundException — исключение для случая "объект не найден в БД".
// Паттерн одинаков во всех сервисах проекта (auth, movie, hall, order, support).
//
// extends RuntimeException:
//   - Unchecked исключение — НЕ требует объявления throws в сигнатуре метода.
//   - Spring @Transactional автоматически откатывает транзакцию при RuntimeException.
//   - GlobalExceptionHandler перехватывает и возвращает HTTP 404.
public class ResourceNotFoundException extends RuntimeException {

    // Конструктор принимает сообщение об ошибке.
    // Передаётся в super(message) → сохраняется в Throwable.detailMessage.
    // Доступно через ex.getMessage() в GlobalExceptionHandler.
    // Пример: "Support ticket not found: 999"
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
