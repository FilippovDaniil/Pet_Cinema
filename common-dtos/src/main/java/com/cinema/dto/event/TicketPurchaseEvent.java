package com.cinema.dto.event; // Пакет для Kafka-событий

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Точный тип для денежных сумм

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPurchaseEvent {
    // Kafka-событие: публикуется order-service в топик "ticket-purchase" после успешной оплаты
    // Получатель: notification-service → создаёт уведомление покупателю о покупке билета

    private Long orderId;        // ID оплаченного заказа
    private Long userId;         // ID покупателя — получит уведомление
    private String movieTitle;   // Название фильма — отображается в тексте уведомления
    private String sessionTime;  // Время сеанса в строковом формате (например "2025-06-01 18:00")
    private BigDecimal totalPrice; // Итоговая сумма заказа
}
