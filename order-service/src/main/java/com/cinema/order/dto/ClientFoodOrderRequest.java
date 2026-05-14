package com.cinema.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

// Локальный DTO (в пакете order-service, не в common-dtos) — используется только здесь.
// Запрос клиента на создание заказа с едой онлайн (POST /api/orders/food/client).
// @Data — Lombok: геттеры/сеттеры + equals/hashCode/toString
@Data
public class ClientFoodOrderRequest {

    // @NotEmpty — список позиций не должен быть null и не пустым (хотя бы 1 товар)
    // @Valid — запускает валидацию каждого элемента списка (рекурсивная валидация)
    @NotEmpty(message = "Items list must not be empty")
    private List<@Valid ItemRequest> items;

    // Вложенный статический класс — позиция заказа (товар + количество)
    // static — не нужна ссылка на внешний класс ClientFoodOrderRequest (чисто вспомогательная структура)
    @Data
    public static class ItemRequest {

        // @NotNull — id товара обязателен (нельзя заказать "неизвестно что")
        @NotNull(message = "Food item ID must not be null")
        private Long foodItemId;

        // @Min(1) — количество должно быть хотя бы 1 (нельзя заказать 0 или отрицательное)
        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;
    }
}
