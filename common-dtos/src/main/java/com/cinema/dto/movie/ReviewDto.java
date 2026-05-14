package com.cinema.dto.movie; // Пакет для DTO сервиса фильмов

import com.fasterxml.jackson.annotation.JsonFormat; // Формат даты в JSON
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {
    // DTO отзыва на фильм. Клиент может оставить только один отзыв на каждый фильм.

    private Long id;       // Идентификатор отзыва
    private Long movieId;  // ID фильма, на который написан отзыв
    private Long userId;   // ID пользователя, написавшего отзыв
    private int rating;    // Оценка от 1 до 5 (звёзды)
    private String comment; // Текст отзыва (необязательный)

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt; // Дата и время создания отзыва
}
