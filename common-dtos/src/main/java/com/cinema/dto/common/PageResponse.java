package com.cinema.dto.common; // Пакет для общих DTO

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List; // Стандартный интерфейс коллекции для списка элементов

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> { // Дженерик <T> — универсальный враппер для любого типа данных
                                // Например: PageResponse<MovieDto>, PageResponse<OrderDto>

    private List<T> content;      // Список элементов на текущей странице
    private long totalElements;   // Общее количество записей в БД (не только на текущей странице)
    private int totalPages;       // Общее количество страниц = ceil(totalElements / size)
    private int page;             // Номер текущей страницы (начинается с 0)
    private int size;             // Количество элементов на странице (запрошенный размер)
}
