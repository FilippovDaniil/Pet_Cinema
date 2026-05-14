package com.cinema.dto.order; // Пакет для DTO сервиса заказов

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerTicketOrderRequest {
    // Запрос продавца на оформление билета для клиента (ROLE_SELLER → POST /api/orders/ticket/by-seller)
    // В отличие от TicketOrderRequest: требует clientId, заказ сразу PAID (без Kafka/оплаты)

    @NotNull(message = "Client ID must not be null")
    private Long clientId; // ID клиента, для которого продавец оформляет билет

    @NotNull(message = "Session ID must not be null")
    private Long sessionId; // ID сеанса

    @Positive(message = "Seat row must be a positive number")
    private int seatRow; // Номер ряда

    @Positive(message = "Seat number must be a positive number")
    private int seatNumber; // Номер места

    private List<Long> extraServiceIds; // Доп.услуги (опционально)
}
