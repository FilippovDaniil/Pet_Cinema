package com.cinema.movie.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    // StringRedisTemplate — специализация RedisTemplate<String, String>.
    // Все ключи и значения — строки. В movie-service Redis используется:
    //   1. Кеш списка фильмов: ключ "movies:list:all", значение = JSON-строка PageResponse<MovieDto>
    //   2. Инвалидация кеша при изменении фильма (delete ключа)
    // RedisConnectionFactory создаётся Spring Boot автоматически на основе application.yml:
    //   spring.data.redis.host / .port
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    // ObjectMapper (Jackson) — конвертер Java ↔ JSON.
    // Нужен в MovieService для:
    //   - сериализации   PageResponse<MovieDto> → JSON-строка перед записью в Redis
    //   - десериализации JSON-строка → PageResponse<MovieDto> при чтении из Redis кеша
    //
    // JavaTimeModule — расширение Jackson для Java 8+ типов дат/времени (LocalDateTime, LocalDate и т.д.).
    // Без него LocalDateTime сериализуется как массив [2025, 5, 14, 10, 30, 0], что неудобно.
    // С модулем: "2025-05-14T10:30:00" — ISO-8601 строка.
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Поддержка java.time.* типов
        return mapper;
    }
}
