package com.cinema.dto.movie; // Пакет для DTO сервиса фильмов

import jakarta.validation.constraints.NotBlank; // Строка не null и не пустая
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateRequest {
    // Запрос на добавление комментария (только ROLE_CLIENT)

    @NotBlank(message = "Comment text must not be blank")
    private String text; // Текст комментария — обязательное поле
}
