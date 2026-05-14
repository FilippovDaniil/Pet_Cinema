package com.cinema.movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// @SpringBootApplication — мета-аннотация, которая включает сразу три вещи:
//   1. @Configuration       — этот класс является источником бинов Spring
//   2. @EnableAutoConfiguration — Spring Boot сам настраивает DataSource, JPA, Security и т.д. по classpath
//   3. @ComponentScan       — сканирует пакет com.cinema.movie и вложенные, регистрирует @Component/@Service/@Repository
@SpringBootApplication

// @EnableDiscoveryClient — регистрирует этот сервис в Eureka Server (service-discovery:8761)
// После старта Eureka знает: "movie-service живёт на ip:8082"
// api-gateway может обратиться к нему как lb://movie-service (lb = load-balanced)
@EnableDiscoveryClient
public class MovieServiceApplication {

    // Точка входа JVM. SpringApplication.run() поднимает встроенный Tomcat на порту 8082,
    // инициализирует Spring Context, запускает DataLoader (CommandLineRunner)
    public static void main(String[] args) {
        SpringApplication.run(MovieServiceApplication.class, args);
    }
}
