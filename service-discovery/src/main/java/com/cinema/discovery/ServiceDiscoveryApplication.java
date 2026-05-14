package com.cinema.discovery; // Пакет сервиса обнаружения (Service Discovery)

import org.springframework.boot.SpringApplication;                           // Запуск Spring Boot приложения
import org.springframework.boot.autoconfigure.SpringBootApplication;         // Мета-аннотация: @Configuration + @EnableAutoConfiguration + @ComponentScan
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;   // Включает Eureka Server — реестр микросервисов

@SpringBootApplication // Запускает автоконфигурацию Spring Boot и сканирование компонентов в пакете com.cinema.discovery
@EnableEurekaServer    // Превращает это приложение в Eureka Server — центральный реестр, куда все сервисы регистрируются
public class ServiceDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceDiscoveryApplication.class, args); // Запускает встроенный Tomcat на порту 8761 (из application.yml)
    }
}
