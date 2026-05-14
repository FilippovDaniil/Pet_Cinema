package com.cinema.movie.config;

import com.cinema.movie.entity.*;
import com.cinema.movie.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// @Slf4j — Lombok: создаёт поле "private static final Logger log = LoggerFactory.getLogger(DataLoader.class)"
// @Component — Spring создаёт бин этого класса
// CommandLineRunner — интерфейс Spring: метод run() вызывается ПОСЛЕ запуска приложения (после поднятия контекста)
// Идемпотентность: метод run() безопасен при повторном запуске — данные создаются только если их нет
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    // Spring внедряет репозитории через конструктор (Lombok @RequiredArgsConstructor)
    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;

    // Карта постеров: название фильма → URL картинки.
    // picsum.photos — сервис случайных изображений; seed=starwars обеспечивает
    // постоянство (всегда одна и та же картинка для одного seed).
    // Размер 400x600 — стандартный формат постера (ширина × высота).
    private static final Map<String, String> POSTER_URLS = Map.of(
            "Звёздные войны",  "https://picsum.photos/seed/starwars/400/600",
            "Интерстеллар",    "https://picsum.photos/seed/interstellar/400/600",
            "3D Ужасы",        "https://picsum.photos/seed/horror3d/400/600",
            "Аватар 5D",       "https://picsum.photos/seed/avatar5d/400/600",
            "Комедия дня",     "https://picsum.photos/seed/comedy/400/600"
    );

    // @Transactional: весь метод run() выполняется в одной транзакции БД.
    // Если что-то упадёт — откат всех изменений (атомарность).
    @Override
    @Transactional
    public void run(String... args) {
        if (genreRepository.count() == 0) {
            // Жанры отсутствуют → БД пустая → загружаем начальные данные
            loadData();
        } else {
            // Данные уже есть (повторный запуск приложения) → пропускаем создание
            log.info("Data already loaded, skipping initial seed.");
        }
        // Дозаполняем постеры: если какой-то фильм остался без постера (миграция старых данных)
        // или постер ссылается на placeholder.com — устанавливаем правильный URL
        backfillMissingPosters();
    }

    // Обходит все фильмы и обновляет отсутствующие/устаревшие URL постеров.
    // Это нужно для миграции: если приложение было запущено с первой версией кода,
    // где использовался placeholder.com, а сейчас переходим на picsum.photos.
    private void backfillMissingPosters() {
        movieRepository.findAll().forEach(movie -> {
            String url = movie.getPosterUrl();
            // Условия обновления: постера нет, URL пустой, или старый placeholder
            boolean needsUpdate = url == null || url.isBlank() || url.contains("placeholder.com");
            if (needsUpdate) {
                // Ищем URL по названию фильма; если не знаем — генерируем по id
                String newUrl = POSTER_URLS.getOrDefault(
                        movie.getTitle(),
                        "https://picsum.photos/seed/movie" + movie.getId() + "/400/600"
                );
                movie.setPosterUrl(newUrl);
                movieRepository.save(movie);
                log.info("Set poster for '{}': {}", movie.getTitle(), newUrl);
            }
        });
    }

    // Создаёт начальные данные: 3 жанра + 5 фильмов + 2 отзыва + 2 комментария.
    // movie1 сохраняется в переменную, чтобы использовать её id для отзывов и комментариев.
    private void loadData() {
        log.info("Loading initial data for movie-service...");

        // Сохраняем жанры. genre.id генерирует PostgreSQL (SERIAL).
        Genre action = genreRepository.save(Genre.builder().name("Action").build());
        Genre drama  = genreRepository.save(Genre.builder().name("Drama").build());
        Genre comedy = genreRepository.save(Genre.builder().name("Comedy").build());
        log.info("Created 3 genres: Action, Drama, Comedy");

        // Дополнительная проверка: жанры могут существовать (граничный случай), а фильмы — нет
        if (movieRepository.count() > 0) {
            log.info("Movies already exist, skipping movie creation.");
            return;
        }

        // Первый фильм сохраняем в переменную — нужен его id для отзывов/комментариев ниже.
        // Остальные 4 фильма нам как объекты не нужны.
        Movie movie1 = movieRepository.save(Movie.builder()
                .title("Звёздные войны")
                .description("Эпическая космическая опера о противостоянии добра и зла.")
                .posterUrl(POSTER_URLS.get("Звёздные войны"))
                .durationMinutes(122)
                .type(MovieType.THREE_D)    // Формат 3D → зал THREE_D
                .genres(List.of(action))    // List.of() — неизменяемый список (достаточно для создания)
                .build());

        movieRepository.save(Movie.builder()
                .title("Интерстеллар")
                .description("Путешествие сквозь червоточину в поисках нового дома для человечества.")
                .posterUrl(POSTER_URLS.get("Интерстеллар"))
                .durationMinutes(169)
                .type(MovieType.TWO_D)
                .genres(List.of(drama))
                .build());

        movieRepository.save(Movie.builder()
                .title("3D Ужасы")
                .description("Захватывающий фильм ужасов в формате 3D.")
                .posterUrl(POSTER_URLS.get("3D Ужасы"))
                .durationMinutes(95)
                .type(MovieType.THREE_D)
                .genres(List.of(action))
                .build());

        movieRepository.save(Movie.builder()
                .title("Аватар 5D")
                .description("Погружение в мир Пандоры в формате 5D.")
                .posterUrl(POSTER_URLS.get("Аватар 5D"))
                .durationMinutes(180)
                .type(MovieType.FIVE_D)     // 5D — самый дорогой и иммерсивный формат
                .genres(List.of(action))
                .build());

        movieRepository.save(Movie.builder()
                .title("Комедия дня")
                .description("Лёгкая комедия для хорошего настроения.")
                .posterUrl(POSTER_URLS.get("Комедия дня"))
                .durationMinutes(100)
                .type(MovieType.TWO_D)
                .genres(List.of(comedy))
                .build());

        log.info("Created 5 movies");

        // Демо-отзывы для первого фильма (userId=1 — client1, userId=2 — seller1 из auth-service DataLoader)
        reviewRepository.save(Review.builder()
                .movieId(movie1.getId()).userId(1L) // movie1.getId() — id, присвоенный PostgreSQL после save()
                .rating(4).comment("Отличный фильм, но немного затянут.").build());

        reviewRepository.save(Review.builder()
                .movieId(movie1.getId()).userId(2L)
                .rating(5).comment("Шедевр! Обязательно к просмотру.").build());

        // Демо-комментарии (userId=1 и userId=3 — admin1 из auth-service DataLoader)
        commentRepository.save(Comment.builder()
                .movieId(movie1.getId()).userId(1L)
                .text("Кто смотрел — тот понял! Это нечто особенное.").build());

        commentRepository.save(Comment.builder()
                .movieId(movie1.getId()).userId(3L)
                .text("Классика жанра, пересматриваю уже в 10-й раз.").build());

        log.info("DataLoader finished successfully.");
    }
}
