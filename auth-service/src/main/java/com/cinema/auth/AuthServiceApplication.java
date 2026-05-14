package com.cinema.auth; // Корневой пакет auth-service

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient; // Регистрирует сервис в Eureka

@SpringBootApplication // Автоконфигурация Spring Boot + сканирование компонентов в пакете com.cinema.auth
@EnableDiscoveryClient // Подключает Eureka Client: сервис зарегистрируется в реестре как "auth-service"
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args); // Запускает Tomcat на порту 8081
    }
}
