package com.cinema.support;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// @SpringBootApplication — мета-аннотация, объединяет три аннотации:
//   @Configuration    — этот класс является источником Spring-бинов
//   @EnableAutoConfiguration — Spring Boot автоматически настраивает всё что находит в classpath
//     (JPA, Security, Kafka, Jackson и т.д.)
//   @ComponentScan    — сканирует пакет com.cinema.support и все подпакеты
//     на наличие @Component, @Service, @Repository, @Controller
// В отличие от order-service — здесь НЕТ @EnableAsync:
//   support-service не использует асинхронные операции (нет InternalPaymentService),
//   поэтому ThreadPoolTaskExecutor не нужен.
@SpringBootApplication

// @EnableDiscoveryClient — регистрирует этот сервис в Eureka Server.
// При старте сервис отправляет POST на http://eureka-host:8761/eureka/apps/SUPPORT-SERVICE.
// Другие сервисы могут найти support-service по имени через lb://support-service.
// В данном сервисе: api-gateway маршрутизирует /api/support/** → lb://support-service.
@EnableDiscoveryClient
public class SupportServiceApplication {

    // main — точка входа Java процесса.
    // SpringApplication.run() запускает встроенный Tomcat на порту 8085 (из application.yml),
    // создаёт Spring ApplicationContext, выполняет CommandLineRunner (DataLoader),
    // регистрирует сервис в Eureka, подключается к PostgreSQL и Kafka.
    public static void main(String[] args) {
        SpringApplication.run(SupportServiceApplication.class, args);
    }
}
