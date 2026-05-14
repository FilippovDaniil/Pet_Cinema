package com.cinema.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Универсальная обёртка для пагинированного ответа.
// Дублирует common-dtos/PageResponse<T> намеренно — movie-service имеет локальную копию
// потому что сериализует/десериализует её через ObjectMapper при работе с Redis кешем.
// Generics: <T> — тип элементов в content (например, MovieDto).
// Используется в MovieService.getAllMovies() и MovieController.getAllMovies().
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;       // Список элементов текущей страницы
    private int page;              // Номер текущей страницы (с 0)
    private int size;              // Размер страницы (сколько элементов запросили)
    private long totalElements;    // Всего элементов в БД (нужно для пагинации на фронтенде)
    private int totalPages;        // Всего страниц = Math.ceil(totalElements / size)
    private boolean last;          // true — это последняя страница (не нужно загружать следующую)
}
