package com.cinema.dto.order; // Пакет для DTO сервиса заказов

import com.fasterxml.jackson.annotation.JsonFormat; // Формат даты в JSON
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    // DTO заказа — возвращается клиенту (история заказов, детали заказа)

    private Long id;           // Идентификатор заказа
    private Long userId;       // ID покупателя
    private Long sellerId;     // ID продавца (null если клиент купил сам через автомат)
    private String orderType;  // Тип заказа: "TICKET", "FOOD", "MIXED"
    private String status;     // Статус: "PENDING" (ожидает оплаты), "PAID", "CANCELLED"
    private BigDecimal totalPrice; // Итоговая сумма заказа
    private List<OrderItemDto> items; // Список позиций заказа (билеты + еда)

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt; // Дата и время создания заказа
}
