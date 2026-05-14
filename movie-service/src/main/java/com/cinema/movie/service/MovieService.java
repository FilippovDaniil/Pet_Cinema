package com.cinema.movie.service;

import com.cinema.dto.movie.*;
import com.cinema.movie.dto.MovieUpdateEvent;
import com.cinema.movie.dto.PageResponse;
import com.cinema.movie.entity.*;
import com.cinema.movie.exception.AlreadyExistsException;
import com.cinema.movie.exception.ResourceNotFoundException;
import com.cinema.movie.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

// @Slf4j — Lombok: создаёт log = LoggerFactory.getLogger(MovieService.class)
// @Service — бин слоя бизнес-логики
// @RequiredArgsConstructor — конструктор со всеми final-полями для DI
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;
    private final StringRedisTemplate redisTemplate; // Для кеширования и инвалидации
    private final ObjectMapper objectMapper;          // Для сериализации/десериализации JSON ↔ объект
    private final KafkaTemplate<String, Object> kafkaTemplate; // Для публикации событий в Kafka

    @Value("${redis.movies-cache-key}")   // "movies:list:all" из application.yml
    private String moviesCacheKey;

    @Value("${redis.movies-cache-ttl}")   // 600 секунд (10 минут) из application.yml
    private long moviesCacheTtl;

    // Получение всех фильмов с фильтрацией и пагинацией.
    // Логика кеширования: кеш используется ТОЛЬКО для запроса "без фильтров, первая страница, 10 элементов".
    // Это самый распространённый запрос (главная страница). Все фильтрованные запросы идут в БД.
    @Transactional(readOnly = true)
    public PageResponse<MovieDto> getAllMovies(String genre, String type, Integer durationMax, int page, int size) {
        // Проверяем: это "стандартный" запрос без фильтров? → можем попробовать кеш.
        if (genre == null && type == null && durationMax == null && page == 0 && size == 10) {
            String cached = redisTemplate.opsForValue().get(moviesCacheKey); // Читаем строку из Redis
            if (cached != null) {
                try {
                    // Десериализуем JSON-строку обратно в PageResponse<MovieDto>.
                    // TypeReference нужен для generic-типа: без него Jackson не знает, что T = MovieDto.
                    return objectMapper.readValue(cached, new TypeReference<PageResponse<MovieDto>>() {});
                } catch (Exception e) {
                    // Кеш повреждён — просто пропускаем и идём в БД. Не падаем с ошибкой.
                    log.warn("Failed to deserialize movies cache, fetching from DB", e);
                }
            }
        }

        // Конвертируем строку типа ("TWO_D") в enum MovieType.
        // valueOf() бросает IllegalArgumentException при неизвестном значении — ловим и логируем.
        MovieType movieType = null;
        if (type != null) {
            try {
                movieType = MovieType.valueOf(type); // "TWO_D" → MovieType.TWO_D
            } catch (IllegalArgumentException e) {
                log.warn("Invalid movie type filter: {}", type);
                // movieType остаётся null → фильтр по типу не применяется (пользователь ввёл некорректное значение)
            }
        }

        // PageRequest.of(page, size) — создаёт объект пагинации.
        // Hibernate добавит: LIMIT size OFFSET (page * size)
        Pageable pageable = PageRequest.of(page, size);
        Page<Movie> moviePage = movieRepository.findAllWithFilters(genre, movieType, durationMax, pageable);

        // Для каждого фильма делаем дополнительный SELECT для отзывов (N+1 запросов).
        // Для учебного проекта это приемлемо. В продакшене — JOIN FETCH или BatchSize.
        List<MovieDto> dtos = moviePage.getContent().stream()
                .map(m -> toDto(m, reviewRepository.findByMovieId(m.getId())))
                .collect(Collectors.toList());

        // Собираем Page Spring Data → наш PageResponse (более простая структура для API).
        // moviePage.getNumber() = текущая страница (0-based), .getSize() = размер, и т.д.
        PageResponse<MovieDto> response = PageResponse.<MovieDto>builder()
                .content(dtos)
                .page(moviePage.getNumber())
                .size(moviePage.getSize())
                .totalElements(moviePage.getTotalElements()) // Всего фильмов в БД (с учётом фильтров)
                .totalPages(moviePage.getTotalPages())
                .last(moviePage.isLast())
                .build();

        // Сохраняем результат в Redis только для стандартного запроса без фильтров.
        // Duration.ofSeconds() — TTL для ключа: через 600 сек Redis автоматически удалит запись.
        if (genre == null && type == null && durationMax == null && page == 0 && size == 10) {
            try {
                String json = objectMapper.writeValueAsString(response); // PageResponse<MovieDto> → JSON-строка
                redisTemplate.opsForValue().set(moviesCacheKey, json, Duration.ofSeconds(moviesCacheTtl));
            } catch (Exception e) {
                log.warn("Failed to cache movies list", e); // Ошибка кеширования не должна ломать запрос
            }
        }

        return response;
    }

    // Получение одного фильма по id со всеми отзывами и средним рейтингом.
    // orElseThrow: если Optional пустой (фильма нет) → выбрасываем ResourceNotFoundException → 404.
    @Transactional(readOnly = true)
    public MovieDto getMovieById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
        List<Review> reviews = reviewRepository.findByMovieId(id); // Все отзывы к фильму
        return toDto(movie, reviews); // Включает расчёт среднего рейтинга
    }

    // Создание нового фильма.
    // Порядок действий: разрешить жанры → создать Movie → сохранить → инвалидировать кеш → опубликовать событие.
    @Transactional
    public MovieDto createMovie(MovieCreateRequest req, Long userId) {
        List<Genre> genres = resolveGenres(req.getGenreIds()); // Загружаем Genre из БД по id
        MovieType type = MovieType.valueOf(req.getType());     // Конвертируем строку в enum

        Movie movie = Movie.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .posterUrl(req.getPosterUrl())
                .durationMinutes(req.getDurationMinutes())
                .type(type)
                .genres(genres) // ManyToMany: Hibernate создаст записи в movie_genres
                .build();

        Movie saved = movieRepository.save(movie); // INSERT + получаем сгенерированный id
        invalidateCache();                          // Удаляем кеш "movies:list:all" из Redis
        publishEvent(saved.getId(), "CREATED", saved.getTitle()); // Публикуем в Kafka "movie-update"
        return toDto(saved, List.of()); // Новый фильм — отзывов ещё нет
    }

    // Обновление существующего фильма.
    // Принцип: загружаем сущность, меняем поля через сеттеры, Spring JPA сам делает UPDATE при commit.
    // (Это называется "dirty checking" — Hibernate замечает изменения и генерирует UPDATE автоматически.)
    // movieRepository.save(movie) явный вызов нужен только если хотим сразу получить обновлённый объект.
    @Transactional
    public MovieDto updateMovie(Long id, MovieCreateRequest req) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));

        // Обновляем все поля (полное обновление — не PATCH, а PUT)
        movie.setTitle(req.getTitle());
        movie.setDescription(req.getDescription());
        movie.setPosterUrl(req.getPosterUrl());
        movie.setDurationMinutes(req.getDurationMinutes());
        movie.setType(MovieType.valueOf(req.getType()));

        // Жанры обновляем только если они переданы в запросе (не null)
        if (req.getGenreIds() != null) {
            movie.setGenres(resolveGenres(req.getGenreIds())); // Заменяем список жанров целиком
        }

        Movie updated = movieRepository.save(movie); // UPDATE movies SET ... WHERE id = ?
        invalidateCache();                            // Кеш устарел → удаляем
        publishEvent(updated.getId(), "UPDATED", updated.getTitle()); // Уведомляем api-gateway
        return toDto(updated, reviewRepository.findByMovieId(updated.getId())); // Возвращаем с отзывами
    }

    // Удаление фильма.
    // Важно: связи в movie_genres удалятся автоматически (Hibernate управляет ManyToMany).
    // Отзывы и комментарии НЕ удаляются каскадно — orphan records остаются в таблицах.
    // Для учебного проекта это приемлемо.
    @Transactional
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
        movieRepository.delete(movie); // DELETE FROM movies WHERE id = ? + DELETE FROM movie_genres WHERE movie_id = ?
        invalidateCache();
        publishEvent(id, "DELETED", movie.getTitle()); // Событие с action="DELETED"
    }

    // Добавление отзыва к фильму.
    // Защита: 1) фильм существует, 2) пользователь ещё не оставлял отзыв на этот фильм.
    @Transactional
    public ReviewDto addReview(Long movieId, ReviewCreateRequest req, Long userId) {
        if (!movieRepository.existsById(movieId)) { // SELECT EXISTS(...) — быстрее чем findById
            throw new ResourceNotFoundException("Movie not found with id: " + movieId);
        }
        if (reviewRepository.existsByMovieIdAndUserId(movieId, userId)) {
            // Бизнес-правило: один пользователь = один отзыв на фильм
            throw new AlreadyExistsException("User has already reviewed this movie");
        }

        Review review = Review.builder()
                .movieId(movieId)
                .userId(userId)       // userId берём из JWT-токена (установлен в SecurityContext)
                .rating(req.getRating())   // 1-5
                .comment(req.getComment()) // Текст отзыва
                .build();
        // createdAt устанавливается автоматически через @PrePersist в Review.java

        Review saved = reviewRepository.save(review); // INSERT INTO reviews ...
        return toReviewDto(saved);
    }

    // Добавление комментария к фильму.
    // В отличие от отзыва: нет ограничения на количество комментариев от одного пользователя.
    @Transactional
    public CommentDto addComment(Long movieId, CommentCreateRequest req, Long userId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResourceNotFoundException("Movie not found with id: " + movieId);
        }

        Comment comment = Comment.builder()
                .movieId(movieId)
                .userId(userId)
                .text(req.getText())
                .build();

        Comment saved = commentRepository.save(comment);
        return toCommentDto(saved);
    }

    // Загружает Genre-объекты из БД по списку id.
    // Stream::map() применяет функцию к каждому id.
    // Если хоть один genre не найден — бросаем ResourceNotFoundException (запрос откатывается).
    private List<Genre> resolveGenres(List<Long> genreIds) {
        return genreIds.stream()
                .map(gid -> genreRepository.findById(gid)
                        .orElseThrow(() -> new ResourceNotFoundException("Genre not found with id: " + gid)))
                .collect(Collectors.toList());
    }

    // Инвалидация Redis кеша: удаляем ключ "movies:list:all".
    // Следующий запрос GET /api/movies (без фильтров) получит свежие данные из БД и снова закеширует.
    private void invalidateCache() {
        redisTemplate.delete(moviesCacheKey); // DEL movies:list:all
    }

    // Публикует событие MovieUpdateEvent в Kafka топик "movie-update".
    // Ключ сообщения = movieId.toString() — по ключу Kafka определяет раздел (partition).
    // Все события одного фильма попадают в один partition → потребитель обрабатывает их по порядку.
    // Обёрнуто в try-catch: ошибка Kafka не должна прерывать основную операцию с БД.
    private void publishEvent(Long movieId, String action, String title) {
        try {
            MovieUpdateEvent event = MovieUpdateEvent.builder()
                    .movieId(movieId)
                    .action(action) // "CREATED", "UPDATED" или "DELETED"
                    .title(title)
                    .build();
            kafkaTemplate.send("movie-update", String.valueOf(movieId), event);
            // kafkaTemplate.send() возвращает CompletableFuture — не ждём результата (fire-and-forget)
        } catch (Exception e) {
            // Kafka недоступна — логируем предупреждение, но не ломаем HTTP-ответ клиенту
            log.warn("Failed to publish movie update event for movieId={}", movieId, e);
        }
    }

    // Маппер Movie → MovieDto.
    // Вычисляет средний рейтинг: stream().mapToInt().average() возвращает OptionalDouble.
    // orElse(0.0) — если отзывов нет, возвращает 0.0. Но мы передаём null вместо 0.0
    // чтобы фронтенд различал "нет рейтинга" и "рейтинг 0".
    private MovieDto toDto(Movie movie, List<Review> reviews) {
        double avgRating = reviews.stream()
                .mapToInt(Review::getRating) // Review → int (rating)
                .average()                   // IntStream → OptionalDouble
                .orElse(0.0);                // Если пусто — 0.0 (но мы проверяем reviews.isEmpty() ниже)

        // Преобразуем список Genre-объектов в список строк (только имена)
        List<String> genreNames = movie.getGenres().stream()
                .map(Genre::getName) // Genre → String
                .collect(Collectors.toList());

        return MovieDto.builder()
                .id(movie.getId())
                .title(movie.getTitle())
                .description(movie.getDescription())
                .posterUrl(movie.getPosterUrl())
                .durationMinutes(movie.getDurationMinutes())
                .type(movie.getType().name()) // MovieType.TWO_D → "TWO_D" (строка для API)
                .genres(genreNames)
                .averageRating(reviews.isEmpty() ? null : avgRating)
                // null = нет отзывов (фронтенд покажет "нет оценок")
                // число = средняя оценка (например, 4.5)
                .build();
    }

    // Маппер Review → ReviewDto (для API ответа)
    private ReviewDto toReviewDto(Review review) {
        return ReviewDto.builder()
                .id(review.getId())
                .movieId(review.getMovieId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt()) // Временная метка из @PrePersist
                .build();
    }

    // Маппер Comment → CommentDto (для API ответа)
    private CommentDto toCommentDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .movieId(comment.getMovieId())
                .userId(comment.getUserId())
                .text(comment.getText())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
