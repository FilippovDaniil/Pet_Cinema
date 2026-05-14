package com.cinema.movie.repository;

import com.cinema.movie.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Spring Data генерирует: SELECT * FROM comments WHERE movie_id = ?
    // Возвращает все комментарии к конкретному фильму.
    // В отличие от ReviewRepository, здесь нет ограничения "один комментарий на пользователя" —
    // один пользователь может оставить сколько угодно комментариев.
    List<Comment> findByMovieId(Long movieId);
}
