package com.cinema.dto.order; // Пакет для DTO сервиса заказов

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDto {
    // DTO одной позиции заказа. В зависимости от itemType заполнены разные поля.

    private Long id;       // Идентификатор позиции
    private Long orderId;  // ID заказа, к которому относится позиция

    private String itemType; // "TICKET" или "FOOD" — определяет какие поля заполнены

    // Поля для билетов (itemType = "TICKET"):
    private Long ticketSessionId;      // ID сеанса
    private int ticketSeatRow;         // Номер ряда
    private int ticketSeatNumber;      // Номер места
    private String ticketExtraServices; // JSON-строка с выбранными доп.услугами

    // Поля для еды (itemType = "FOOD"):
    private Long foodItemId; // ID позиции меню

    private int quantity;         // Количество (для еды > 1, для билета = 1)
    private BigDecimal price;     // Цена за единицу (без учёта quantity)
}
