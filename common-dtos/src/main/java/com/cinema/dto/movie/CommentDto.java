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
public class CommentDto {
    // DTO комментария к фильму. Отличие от Review: нет рейтинга, можно оставлять несколько

    private Long id;       // Идентификатор комментария
    private Long movieId;  // ID фильма, к которому оставлен комментарий
    private Long userId;   // ID пользователя, написавшего комментарий
    private String text;   // Текст комментария

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt; // Время создания комментария
}
