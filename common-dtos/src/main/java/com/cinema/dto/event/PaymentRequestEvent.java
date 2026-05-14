package com.cinema.dto.event; // Пакет для Kafka-событий

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Точный тип для денежных сумм (без ошибок округления float/double)

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestEvent {
    // Kafka-событие: публикуется order-service в топик "payment-request"
    // Получатель: payment-simulator → ждёт 3 сек → POST /api/orders/webhook/payment

    private Long orderId; // ID заказа, для которого нужно провести оплату
    private Long userId;  // ID покупателя
    private BigDecimal amount; // Сумма к оплате (копируется из Order.totalPrice)
}
