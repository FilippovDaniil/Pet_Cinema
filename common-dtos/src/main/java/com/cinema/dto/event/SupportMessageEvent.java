package com.cinema.dto.event; // Пакет для Kafka-событий

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessageEvent {
    // Kafka-событие: публикуется support-service в топик "support-message"
    // Получатель: notification-service → создаёт уведомление получателю сообщения

    private Long ticketId;    // ID тикета поддержки, в котором отправлено сообщение
    private Long senderId;    // ID отправителя (клиент или администратор)
    private String content;   // Текст сообщения (для отображения в уведомлении)
    private Long recipientId; // ID получателя уведомления:
                              //   если отправитель CLIENT → recipientId = adminId тикета
                              //   если отправитель ADMIN  → recipientId = clientId тикета
}
