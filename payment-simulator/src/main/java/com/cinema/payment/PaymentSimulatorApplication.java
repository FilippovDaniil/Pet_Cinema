package com.cinema.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

// @SpringBootApplication — точка входа Spring Boot:
//   - @ComponentScan(basePackages = "com.cinema.payment") — сканирует все @Component, @Service и т.д.
//   - @EnableAutoConfiguration — автоматически конфигурирует Spring по classpath (Web, Kafka, Eureka...)
//   - @Configuration — позволяет объявлять @Bean методы в этом классе
@SpringBootApplication

// @EnableDiscoveryClient — регистрирует сервис в Eureka Server.
// payment-simulator регистрируется под именем "payment-simulator" (из application.yml spring.application.name).
// Это позволяет другим сервисам находить payment-simulator через Eureka, хотя в данном проекте
// payment-simulator сам только ВЫЗЫВАЕТ order-service по прямому URL (не через lb://).
@EnableDiscoveryClient

// @EnableAsync — включает поддержку асинхронного выполнения через @Async аннотацию.
// Без этой аннотации @Async в PaymentRequestConsumer.processPaymentAsync() будет ИГНОРИРОВАТЬСЯ
// и метод выполнится синхронно (блокируя Kafka listener поток).
// ВАЖНО: @EnableAsync продублировано и в AsyncConfig.java (@Configuration класс).
// Это "belt and suspenders" подход: гарантия что @EnableAsync будет активен независимо
// от порядка загрузки конфигурации (аналогичный паттерн в order-service).
@EnableAsync
public class PaymentSimulatorApplication {

    // main — стандартная точка входа Java приложения.
    // SpringApplication.run() запускает Spring ApplicationContext:
    //   1. Сканирует компоненты (PaymentRequestConsumer, AsyncConfig, RestTemplateConfig)
    //   2. Применяет AutoConfiguration (Kafka consumer, Eureka client, Web server)
    //   3. Подключается к Kafka broker и начинает слушать топик "payment-request"
    //   4. Регистрируется в Eureka Server
    public static void main(String[] args) {
        SpringApplication.run(PaymentSimulatorApplication.class, args);
    }
}
