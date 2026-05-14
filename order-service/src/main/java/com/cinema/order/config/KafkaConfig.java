package com.cinema.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// @Configuration — класс содержит Bean определения для Spring контекста
@Configuration
public class KafkaConfig {

    // Bean NewTopic — Spring Kafka автоматически создаёт топик в Kafka при старте приложения
    // если топик ещё не существует. Идемпотентно: если топик есть — ничего не происходит.
    //
    // Топик "payment-request":
    //   publisher: order-service (OrderService публикует PaymentRequestEvent)
    //   consumer:  payment-simulator (слушает и имитирует оплату)
    @Bean
    public NewTopic paymentRequestTopic() {
        return TopicBuilder.name("payment-request")
                .partitions(3)    // 3 партиции — параллельная обработка до 3 consumer instances
                .replicas(1)      // 1 реплика — для dev/test среды (prod нужно минимум 3)
                .build();
    }

    // Топик "ticket-purchase":
    //   publisher: order-service (OrderService публикует TicketPurchaseEvent после оплаты)
    //   consumer:  notification-service (создаёт уведомление пользователю)
    @Bean
    public NewTopic ticketPurchaseTopic() {
        return TopicBuilder.name("ticket-purchase")
                .partitions(3)    // 3 партиции для масштабирования notification-service
                .replicas(1)      // 1 реплика для dev среды
                .build();
    }
}
