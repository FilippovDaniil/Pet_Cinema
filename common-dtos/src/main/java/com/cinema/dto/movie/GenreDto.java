package com.cinema.dto.movie; // Пакет для DTO сервиса фильмов

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenreDto {

    private Long id;     // Идентификатор жанра в БД
    private String name; // Название жанра, например "Action", "Drama", "Comedy"
}
