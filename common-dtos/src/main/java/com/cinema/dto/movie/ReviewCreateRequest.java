package com.cinema.dto.movie; // Пакет для DTO сервиса фильмов

import jakarta.validation.constraints.Max; // Максимальное значение числа
import jakarta.validation.constraints.Min; // Минимальное значение числа
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateRequest {
    // Запрос на создание отзыва (только ROLE_CLIENT, один отзыв на фильм)

    @Min(value = 1, message = "Rating must be at least 1") // Минимум 1 звезда
    @Max(value = 5, message = "Rating must be at most 5")  // Максимум 5 звёзд
    private int rating; // Оценка фильма (1-5)

    private String comment; // Текст отзыва — необязательный, нет валидации
}
