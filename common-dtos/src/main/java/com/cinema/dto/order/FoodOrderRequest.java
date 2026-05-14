package com.cinema.dto.order; // Пакет для DTO сервиса заказов

import jakarta.validation.constraints.Min;      // Минимальное значение числа
import jakarta.validation.constraints.NotEmpty; // Список не null и не пустой
import jakarta.validation.constraints.NotNull;  // Значение не null
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodOrderRequest {
    // Запрос на заказ еды (SELLER → POST /api/orders/food)
    // Оплачивается сразу (статус PAID), без Kafka

    @NotNull(message = "Client ID must not be null")
    private Long clientId; // ID клиента, для которого оформляется заказ еды

    @NotEmpty(message = "Items list must not be empty")
    private List<FoodOrderItemRequest> items; // Список позиций заказа (минимум одна)

    @Data        // Вложенный статический класс — тоже DTO, не JPA-сущность
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodOrderItemRequest { // static — не нужен экземпляр внешнего класса

        @NotNull(message = "Food item ID must not be null")
        private Long foodItemId; // ID позиции из меню (FoodItem)

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity; // Количество — минимум 1
    }
}
