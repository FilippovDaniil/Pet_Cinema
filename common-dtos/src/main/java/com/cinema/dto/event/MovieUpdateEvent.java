package com.cinema.dto.event; // Пакет для Kafka-событий — сообщения между микросервисами

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieUpdateEvent {
    // Kafka-событие: публикуется movie-service в топик "movie-update"
    // Получатель: api-gateway → удаляет кеш "movies:list:*" из Redis

    private Long movieId; // ID изменённого фильма
    private String action; // Тип изменения: "CREATE", "UPDATE" или "DELETE"
                           // api-gateway использует это чтобы инвалидировать нужные ключи в Redis
}
