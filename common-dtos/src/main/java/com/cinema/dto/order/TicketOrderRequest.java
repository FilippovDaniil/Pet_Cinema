package com.cinema.dto.order; // Пакет для DTO сервиса заказов

import jakarta.validation.constraints.NotNull;  // Значение не null
import jakarta.validation.constraints.Positive; // Число строго > 0
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketOrderRequest {
    // Запрос на покупку билета клиентом (ROLE_CLIENT → POST /api/orders/ticket)
    // После создания заказа → Kafka "payment-request" → payment-simulator → вебхук → PAID

    @NotNull(message = "Session ID must not be null")
    private Long sessionId; // ID сеанса (из hall-service)

    @Positive(message = "Seat row must be a positive number")
    private int seatRow; // Номер ряда (начинается с 1)

    @Positive(message = "Seat number must be a positive number")
    private int seatNumber; // Номер места в ряду (начинается с 1)

    private List<Long> extraServiceIds; // ID дополнительных услуг зала (может быть null или пустым)
}
