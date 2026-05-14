package com.cinema.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Kafka-событие, которое movie-service публикует в топик "movie-update"
// при любом изменении фильма (создание, обновление, удаление).
//
// Поток: movie-service → [Kafka "movie-update"] → api-gateway (CacheInvalidationConsumer)
//
// api-gateway получает событие и удаляет ключ "movies:list:*" из Redis,
// чтобы следующий запрос GET /api/movies получил свежие данные из БД, а не кеш.
//
// Это LOCAL копия события (не из common-dtos) — api-gateway читает его как JSON-строку
// через StringDeserializer и парсит вручную через ObjectMapper.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieUpdateEvent {

    private Long movieId;   // id фильма, который изменился
    private String action;  // "CREATED", "UPDATED" или "DELETED"
    private String title;   // Название фильма (для удобства логирования в api-gateway)
}
