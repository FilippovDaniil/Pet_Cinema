package com.cinema.hall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// Конфигурация Redis и ObjectMapper для hall-service.
// hall-service не кеширует данные в Redis (нет KafkaConsumer для инвалидации),
// однако Redis-зависимость присутствует т.к. добавлена spring-boot-starter-data-redis.
// Этот бин нужен чтобы Spring не падал с ошибкой "no StringRedisTemplate bean found"
// при авто-конфигурации (хотя в hal-service Redis фактически не используется в бизнес-логике).
@Configuration
public class RedisConfig {

    // StringRedisTemplate — специализированный RedisTemplate для String ключей и значений.
    // Работает с Redis напрямую: set(key, value), get(key), delete(key) и т.д.
    // Принимает RedisConnectionFactory — Spring Boot автоматически создаёт его
    // из настроек spring.data.redis.host/port в application.yml.
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    // ObjectMapper — библиотека Jackson для сериализации/десериализации JSON.
    // Используется в DataLoader и потенциально в сервисах для ручного JSON-парсинга.
    //
    // JavaTimeModule — модуль для работы с Java 8 Date/Time API (LocalDateTime и т.д.).
    // Без этого модуля Jackson не умеет сериализовать LocalDateTime (кидает исключение).
    // Регистрируем явно, т.к. не используем @EnableWebMvc (который подключает его автоматически).
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Поддержка LocalDateTime, LocalDate и т.д.
        return mapper;
    }
}
