package com.cinema.order; // Корневой пакет микросервиса заказов

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

// @SpringBootApplication — мета-аннотация = @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan
@SpringBootApplication
// @EnableDiscoveryClient — регистрирует сервис в Eureka.
// order-service нужен в Eureka чтобы:
//   1. Его нашли другие сервисы через lb://order-service
//   2. Он сам мог использовать lb://hall-service в @LoadBalanced RestTemplate
@EnableDiscoveryClient
// @EnableAsync — активирует поддержку @Async методов в классах Spring.
// Требуется для InternalPaymentService.simulatePayment() — асинхронной симуляции оплаты.
// Без этой аннотации @Async ведёт себя как обычный синхронный вызов.
@EnableAsync
public class OrderServiceApplication {

    // Точка входа. SpringApplication.run() запускает:
    //   1. Spring Context с JPA, Security, Kafka, RestTemplate, Async
    //   2. Встроенный Tomcat на порту 8084
    //   3. Регистрацию в Eureka
    //   4. CommandLineRunner (DataLoader) для заполнения меню едой
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
