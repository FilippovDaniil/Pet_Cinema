package com.cinema.support.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// @Configuration — класс содержит @Bean определения.
// Spring обрабатывает его при старте приложения.
@Configuration
// KafkaConfig — конфигурация Kafka для support-service.
// В отличие от order-service (2 топика: payment-request, ticket-purchase),
// support-service создаёт ТОЛЬКО ОДИН топик: "support-message".
public class KafkaConfig {

    // supportMessageTopic — декларирует Kafka топик "support-message".
    // NewTopic — объект описания топика. Spring Kafka Admin автоматически создаёт топик
    //   в Kafka брокере при старте, если он ещё не существует.
    // Если топик уже существует — ничего не происходит (идемпотентно).
    @Bean
    public NewTopic supportMessageTopic() {
        return TopicBuilder.name("support-message")   // имя топика — строка для producers/consumers
                .partitions(3)   // 3 партиции — параллелизм обработки и масштабируемость.
                                 // Сообщения от разных тикетов могут обрабатываться параллельно.
                                 // Ключ = ticketId (String.valueOf(ticketId)) — все сообщения
                                 //   одного тикета попадают в одну партицию (гарантия порядка).
                .replicas(1)     // 1 реплика — в dev/test окружении достаточно.
                                 // В продакшн: replicas=3 для отказоустойчивости.
                .build();        // создаёт NewTopic объект
    }
    // Consumer: notification-service слушает топик "support-message".
    // Событие содержит: ticketId, senderId, content, recipientId.
    // notification-service создаёт уведомление для recipientId.
}
