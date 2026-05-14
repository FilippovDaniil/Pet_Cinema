package com.cinema.auth.config; // Пакет конфигураций auth-service

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;   // Фабрика соединений с Redis
import org.springframework.data.redis.core.StringRedisTemplate;           // Специализированный шаблон для String ключей/значений

@Configuration
public class RedisConfig {
    // Конфигурирует Redis-клиент для auth-service.
    // Используется для хранения blacklist токенов при logout:
    // ключ "blacklist:{refreshToken}" → значение "revoked", TTL = оставшееся время жизни токена

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // RedisConnectionFactory — Spring Boot автоматически создаёт из настроек redis.host/port
        return new StringRedisTemplate(connectionFactory);
        // StringRedisTemplate — это RedisTemplate<String, String>.
        // Удобен когда ключи и значения — строки (не нужна сериализация объектов).
    }
}
