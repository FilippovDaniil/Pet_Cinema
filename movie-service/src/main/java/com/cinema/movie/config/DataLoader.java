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

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;

    private static final Map<String, String> POSTER_URLS = Map.of(
            "Звёздные войны",  "https://picsum.photos/seed/starwars/400/600",
            "Интерстеллар",    "https://picsum.photos/seed/interstellar/400/600",
            "3D Ужасы",        "https://picsum.photos/seed/horror3d/400/600",
            "Аватар 5D",       "https://picsum.photos/seed/avatar5d/400/600",
            "Комедия дня",     "https://picsum.photos/seed/comedy/400/600"
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (genreRepository.count() == 0) {
            loadData();
        } else {
            log.info("Data already loaded, skipping initial seed.");
        }
        backfillMissingPosters();
    }

    private void backfillMissingPosters() {
        movieRepository.findAll().forEach(movie -> {
            String url = movie.getPosterUrl();
            boolean needsUpdate = url == null || url.isBlank() || url.contains("placeholder.com");
            if (needsUpdate) {
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

    private void loadData() {
        log.info("Loading initial data for movie-service...");

        Genre action = genreRepository.save(Genre.builder().name("Action").build());
        Genre drama  = genreRepository.save(Genre.builder().name("Drama").build());
        Genre comedy = genreRepository.save(Genre.builder().name("Comedy").build());
        log.info("Created 3 genres: Action, Drama, Comedy");

        if (movieRepository.count() > 0) {
            log.info("Movies already exist, skipping movie creation.");
            return;
        }

        Movie movie1 = movieRepository.save(Movie.builder()
                .title("Звёздные войны")
                .description("Эпическая космическая опера о противостоянии добра и зла.")
                .posterUrl(POSTER_URLS.get("Звёздные войны"))
                .durationMinutes(122)
                .type(MovieType.THREE_D)
                .genres(List.of(action))
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
                .type(MovieType.FIVE_D)
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

        reviewRepository.save(Review.builder()
                .movieId(movie1.getId()).userId(1L)
                .rating(4).comment("Отличный фильм, но немного затянут.").build());

        reviewRepository.save(Review.builder()
                .movieId(movie1.getId()).userId(2L)
                .rating(5).comment("Шедевр! Обязательно к просмотру.").build());

        commentRepository.save(Comment.builder()
                .movieId(movie1.getId()).userId(1L)
                .text("Кто смотрел — тот понял! Это нечто особенное.").build());

        commentRepository.save(Comment.builder()
                .movieId(movie1.getId()).userId(3L)
                .text("Классика жанра, пересматриваю уже в 10-й раз.").build());

        log.info("DataLoader finished successfully.");
    }
}
