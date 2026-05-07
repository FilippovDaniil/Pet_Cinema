package com.cinema.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic paymentRequestTopic() {
        return TopicBuilder.name("payment-request")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ticketPurchaseTopic() {
        return TopicBuilder.name("ticket-purchase")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
