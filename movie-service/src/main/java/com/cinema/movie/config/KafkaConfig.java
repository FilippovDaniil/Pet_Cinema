package com.cinema.movie.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

// movie-service — только PRODUCER (публикует события в Kafka).
// Публикует MovieUpdateEvent в топик "movie-update" при создании/изменении/удалении фильма.
// Слушатель этих событий — api-gateway (CacheInvalidationConsumer), который удаляет кеш в Redis.
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}") // Адрес Kafka брокера: localhost:9092 или kafka:9092 в Docker
    private String bootstrapServers;

    // ProducerFactory — фабрика Kafka-продюсеров. Создаёт продюсеры с заданной конфигурацией.
    // KafkaTemplate использует эту фабрику для отправки сообщений.
    // Generics <String, Object>:
    //   String — тип ключа сообщения (movieId.toString())
    //   Object — тип значения (MovieUpdateEvent, сериализуется в JSON)
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers); // Адрес брокера
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Ключ сообщения сериализуется как строка (movieId → "42")
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Значение сериализуется как JSON: MovieUpdateEvent → {"movieId":42,"action":"UPDATED","title":"..."}
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        // ADD_TYPE_INFO_HEADERS = false: не добавлять заголовок "__TypeId__" в сообщение.
        // Этот заголовок нужен только если потребитель тоже Spring (для десериализации в конкретный класс).
        // api-gateway читает сообщение просто как JSON строку — заголовок не нужен и только мешает.
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    // KafkaTemplate — основной класс для отправки сообщений в Kafka.
    // Использование в MovieService: kafkaTemplate.send("movie-update", key, event)
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
