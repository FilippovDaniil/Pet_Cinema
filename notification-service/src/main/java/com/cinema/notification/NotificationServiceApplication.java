package com.cinema.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// @SpringBootApplication — мета-аннотация Spring Boot:
//   @Configuration: класс содержит конфигурацию Spring
//   @EnableAutoConfiguration: автоматически настраивает JPA, Security, Kafka Consumer, Jackson
//   @ComponentScan: сканирует пакет com.cinema.notification на @Component, @Service, @Repository и т.д.
@SpringBootApplication

// @EnableDiscoveryClient — регистрирует этот сервис в Eureka Server.
// api-gateway маршрутизирует /api/notifications/** → lb://notification-service.
// notification-service не делает вызовы к другим сервисам через Eureka —
//   только регистрируется для обнаружения другими сервисами.
@EnableDiscoveryClient
public class NotificationServiceApplication {

    // main — точка входа.
    // SpringApplication.run() запускает:
    //   - встроенный Tomcat на порту 8086
    //   - Spring ApplicationContext
    //   - @KafkaListener методы (подписываются на "ticket-purchase" и "support-message")
    //   - Подключение к PostgreSQL (notification_db)
    //   - Регистрацию в Eureka
    // В отличие от auth-service: нет DataLoader (нет демо-данных).
    // В отличие от order-service: нет @EnableAsync (нет асинхронных операций).
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
