package com.cinema.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CacheInvalidationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationConsumer.class);

    private static final String MOVIES_CACHE_PATTERN = "movies:list:*";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public CacheInvalidationConsumer(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = "movie-update", groupId = "gateway-group")
    public void handleMovieUpdate(String message) {
        logger.info("Received movie-update event: {}", message);
        invalidateMoviesCache();
    }

    private void invalidateMoviesCache() {
        redisTemplate.keys(MOVIES_CACHE_PATTERN)
                .flatMap(key -> redisTemplate.delete(key))
                .subscribe(
                        count -> logger.debug("Deleted cache key, count: {}", count),
                        error -> logger.error("Error deleting cache keys with pattern {}: {}", MOVIES_CACHE_PATTERN, error.getMessage())
                );
    }
}
