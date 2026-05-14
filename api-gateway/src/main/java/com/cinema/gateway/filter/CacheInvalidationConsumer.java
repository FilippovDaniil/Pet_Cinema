package com.cinema.gateway.filter; // Пакет фильтров API Gateway

import org.slf4j.Logger;                                               // Интерфейс логгера (SLF4J)
import org.slf4j.LoggerFactory;                                        // Фабрика для создания логгеров
import org.springframework.data.redis.core.ReactiveRedisTemplate;      // Реактивный Redis-клиент (нужен т.к. Gateway — реактивный)
import org.springframework.kafka.annotation.KafkaListener;             // Аннотация: подписывает метод на Kafka-топик
import org.springframework.stereotype.Component;

@Component
public class CacheInvalidationConsumer {
    // Kafka-консьюмер в API Gateway.
    // Слушает топик "movie-update" и инвалидирует Redis-кеш списка фильмов.
    // Зачем в Gateway? Потому что Gateway проксирует все запросы к movie-service
    // и кеширует ответы в Redis. При изменении данных кеш нужно сбросить.

    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationConsumer.class); // Логгер для этого класса

    private static final String MOVIES_CACHE_PATTERN = "movies:list:*";
    // Паттерн ключей Redis для удаления. Символ * — wildcard (все ключи, начинающиеся с "movies:list:")

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    // ReactiveRedisTemplate — реактивная версия RedisTemplate.
    // Нужна потому что Gateway работает на реактивном стеке (не Servlet, а Netty/Reactor)

    public CacheInvalidationConsumer(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate; // Spring инжектирует через конструктор
    }

    @KafkaListener(topics = "movie-update", groupId = "gateway-group")
    // Метод вызывается при каждом новом сообщении в топик "movie-update"
    // groupId = "gateway-group" — идентификатор группы консьюмеров Kafka (все gateway-экземпляры в одной группе)
    public void handleMovieUpdate(String message) {
        logger.info("Received movie-update event: {}", message); // Логируем полученное событие
        invalidateMoviesCache(); // Запускаем инвалидацию кеша
    }

    private void invalidateMoviesCache() {
        redisTemplate.keys(MOVIES_CACHE_PATTERN)  // Находим все ключи, совпадающие с паттерном (возвращает Flux<String>)
                .flatMap(key -> redisTemplate.delete(key)) // Для каждого ключа — удаляем из Redis (Flux<Long>)
                .subscribe(
                        count -> logger.debug("Deleted cache key, count: {}", count), // Успешное удаление — логируем
                        error -> logger.error("Error deleting cache keys with pattern {}: {}",
                                MOVIES_CACHE_PATTERN, error.getMessage()) // Ошибка — логируем
                );
        // subscribe() — "подписываемся" на реактивный поток и запускаем его выполнение
        // Без subscribe() Flux ленив и ничего не выполняет
    }
}
