package com.cinema.hall; // Корневой пакет микросервиса залов и сеансов

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// @SpringBootApplication — «мета-аннотация», объединяет три в одной:
//   @SpringBootConfiguration  — обозначает класс как источник конфигурации (аналог @Configuration)
//   @EnableAutoConfiguration  — автоматически подключает нужные бины (JPA, Security, Web и т.д.)
//   @ComponentScan            — сканирует пакет com.cinema.hall и все вложенные, регистрирует бины
@SpringBootApplication

// @EnableDiscoveryClient — регистрирует этот сервис в реестре Eureka (service-discovery).
// После старта сервис становится виден другим под именем "hall-service" (задаётся в application.yml).
// order-service вызывает hall-service через lb://hall-service/api/sessions/{id} — Eureka находит IP/порт.
@EnableDiscoveryClient
public class HallServiceApplication {

    // Точка входа Java-приложения. SpringApplication.run() поднимает весь Spring Context:
    //   1. Читает application.yml, создаёт окружение (Environment)
    //   2. Запускает встроенный Tomcat на порту 8083
    //   3. Создаёт все @Bean-ы и @Component-ы (JPA, Security, Controllers и т.д.)
    //   4. Регистрируется в Eureka
    //   5. Выполняет CommandLineRunner бины (DataLoader)
    public static void main(String[] args) {
        SpringApplication.run(HallServiceApplication.class, args);
    }
}
