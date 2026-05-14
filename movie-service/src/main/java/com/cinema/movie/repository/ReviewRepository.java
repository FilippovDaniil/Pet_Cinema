package com.cinema.movie.repository;

import com.cinema.movie.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Spring Data генерирует: SELECT * FROM reviews WHERE movie_id = ?
    // Возвращает все отзывы для конкретного фильма.
    // Используется в MovieService.toDto() для подсчёта среднего рейтинга и в getMovieById().
    List<Review> findByMovieId(Long movieId);

    // Spring Data генерирует: SELECT EXISTS(SELECT 1 FROM reviews WHERE movie_id = ? AND user_id = ?)
    // Защита от двойного отзыва: один пользователь может оставить только один отзыв на фильм.
    // Используется в MovieService.addReview() перед сохранением нового отзыва.
    boolean existsByMovieIdAndUserId(Long movieId, Long userId);
}
