package com.cinema.dto.movie; // Пакет для DTO сервиса фильмов

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; // Список жанров

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieDto {
    // DTO фильма — возвращается клиенту, не содержит JPA-сущностей

    private Long id;               // Идентификатор фильма
    private String title;          // Название фильма
    private String description;    // Описание фильма (TEXT в БД — длинный текст)
    private String posterUrl;      // URL постера, например "https://cdn.example.com/poster.jpg"
    private int durationMinutes;   // Длительность в минутах
    private String type;           // Тип: "TWO_D", "THREE_D", "FIVE_D"
    private List<String> genres;   // Список названий жанров (не ID!) — уже раскрыт для удобства фронтенда
    private Double averageRating;  // Средний рейтинг из отзывов (null если отзывов нет)
}
