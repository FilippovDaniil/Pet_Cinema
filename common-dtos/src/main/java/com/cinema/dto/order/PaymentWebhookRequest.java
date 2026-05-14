package com.cinema.dto.order; // Пакет для DTO сервиса заказов

import jakarta.validation.constraints.NotBlank; // Строка не null и не пустая
import jakarta.validation.constraints.NotNull;  // Значение не null
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookRequest {
    // Тело вебхука от payment-simulator → order-service (POST /api/orders/webhook/payment)
    // Этот эндпоинт в whitelist (не требует JWT) — вызывается внутренним сервисом

    @NotNull(message = "Order ID must not be null")
    private Long orderId; // ID заказа, для которого пришёл результат оплаты

    @NotBlank(message = "Status must not be blank")
    private String status; // Результат: "SUCCESS" (в симуляторе всегда SUCCESS)

    @NotBlank(message = "Transaction ID must not be blank")
    private String transactionId; // UUID транзакции (генерируется payment-simulator)
}
