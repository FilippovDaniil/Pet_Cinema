package com.cinema.gateway; // Пакет API Gateway — единая точка входа для всех запросов

import org.springframework.boot.SpringApplication;               // Запуск Spring Boot
import org.springframework.boot.autoconfigure.SpringBootApplication; // Мета-аннотация Spring Boot

@SpringBootApplication // Включает автоконфигурацию + сканирование компонентов в пакете com.cinema.gateway
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
        // Запускает реактивный сервер Netty (НЕ Tomcat!) на порту 8080
        // Spring Cloud Gateway работает на реактивном стеке (Project Reactor + Netty)
    }
}
