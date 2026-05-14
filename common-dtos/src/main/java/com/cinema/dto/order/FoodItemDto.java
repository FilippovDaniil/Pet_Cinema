package com.cinema.dto.order; // Пакет для DTO сервиса заказов

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Точный тип для денег

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodItemDto {
    // DTO позиции меню (еда/напитки в кинотеатре)

    private Long id;            // Идентификатор позиции меню
    private String name;        // Название, например "Попкорн" или "Кола"
    private BigDecimal price;   // Цена (например 250.00)
    private String category;    // Категория: "DRINK", "POPCORN", "SNACK", "OTHER"
}
