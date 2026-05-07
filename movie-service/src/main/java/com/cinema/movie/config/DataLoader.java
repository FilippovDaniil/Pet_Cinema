package com.cinema.movie.config;

import com.cinema.movie.entity.*;
import com.cinema.movie.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (genreRepository.count() == 0) {
            loadData();
        } else {
            log.info("Data already loaded, skipping DataLoader.");
        }
    }

    private void loadData() {
        log.info("Loading initial data for movie-service...");

        // Create genres
        Genre action = genreRepository.save(Genre.builder().name("Action").build());
        Genre drama = genreRepository.save(Genre.builder().name("Drama").build());
        Genre comedy = genreRepository.save(Genre.builder().name("Comedy").build());
        log.info("Created 3 genres: Action, Drama, Comedy");

        if (movieRepository.count() > 0) {
            log.info("Movies already exist, skipping movie creation.");
            return;
        }

        // Create 5 movies
        Movie movie1 = movieRepository.save(Movie.builder()
                .title("Звёздные войны")
                .description("Эпическая космическая опера о противостоянии добра и зла.")
                .posterUrl("https://placeholder.com/star-wars.jpg")
                .durationMinutes(122)
                .type(MovieType.THREE_D)
                .genres(List.of(action))
                .build());

        Movie movie2 = movieRepository.save(Movie.builder()
                .title("Интерстеллар")
                .description("Путешествие сквозь червоточину в поисках нового дома для человечества.")
                .durationMinutes(169)
                .type(MovieType.TWO_D)
                .genres(List.of(drama))
                .build());

        Movie movie3 = movieRepository.save(Movie.builder()
                .title("3D Ужасы")
                .description("Захватывающий фильм ужасов в формате 3D.")
                .durationMinutes(95)
                .type(MovieType.THREE_D)
                .genres(List.of(action))
                .build());

        Movie movie4 = movieRepository.save(Movie.builder()
                .title("Аватар 5D")
                .description("Погружение в мир Пандоры в формате 5D.")
                .durationMinutes(180)
                .type(MovieType.FIVE_D)
                .genres(List.of(action))
                .build());

        Movie movie5 = movieRepository.save(Movie.builder()
                .title("Комедия дня")
                .description("Лёгкая комедия для хорошего настроения.")
                .durationMinutes(100)
                .type(MovieType.TWO_D)
                .genres(List.of(comedy))
                .build());

        log.info("Created 5 movies");

        // Add 2 reviews to first movie
        reviewRepository.save(Review.builder()
                .movieId(movie1.getId())
                .userId(1L)
                .rating(4)
                .comment("Отличный фильм, но немного затянут.")
                .build());

        reviewRepository.save(Review.builder()
                .movieId(movie1.getId())
                .userId(2L)
                .rating(5)
                .comment("Шедевр! Обязательно к просмотру.")
                .build());

        log.info("Created 2 reviews for movie '{}'", movie1.getTitle());

        // Add 2 comments to first movie
        commentRepository.save(Comment.builder()
                .movieId(movie1.getId())
                .userId(1L)
                .text("Кто смотрел - тот понял! Это нечто особенное.")
                .build());

        commentRepository.save(Comment.builder()
                .movieId(movie1.getId())
                .userId(3L)
                .text("Классика жанра, пересматриваю уже в 10-й раз.")
                .build());

        log.info("Created 2 comments for movie '{}'", movie1.getTitle());
        log.info("DataLoader finished successfully.");
    }
}
