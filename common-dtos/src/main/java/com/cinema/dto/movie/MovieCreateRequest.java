package com.cinema.dto.movie; // Пакет для DTO сервиса фильмов

import jakarta.validation.constraints.NotBlank; // Строка не null и не пустая
import jakarta.validation.constraints.NotNull;  // Значение не null
import jakarta.validation.constraints.Positive; // Число строго > 0
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieCreateRequest {
    // Запрос на создание/обновление фильма (только ADMIN)

    @NotBlank(message = "Title must not be blank")
    private String title; // Название фильма

    private String description; // Описание — необязательное (нет @NotBlank)

    private String posterUrl; // URL постера — необязательное

    @Positive(message = "Duration must be a positive number")
    private int durationMinutes; // Длительность > 0 минут

    @NotBlank(message = "Type must not be blank")
    private String type; // Тип: "TWO_D", "THREE_D" или "FIVE_D"

    @NotNull(message = "Genre IDs must not be null")
    private List<Long> genreIds; // Список ID жанров (из genre-справочника)
                                 // movie-service найдёт Genre-объекты по этим ID и создаст ManyToMany-связи
}
