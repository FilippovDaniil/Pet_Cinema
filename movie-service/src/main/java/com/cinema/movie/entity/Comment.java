package com.cinema.movie.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Comment — короткое текстовое высказывание о фильме (в отличие от Review, нет рейтинга).
// Пользователь может оставлять неограниченное количество комментариев к одному фильму
// (в отличие от Review, где действует ограничение "один отзыв на фильм на пользователя").
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movie_id", nullable = false) // id фильма — скалярный FK (не @ManyToOne, см. пояснение в Review.java)
    private Long movieId;

    @Column(name = "user_id", nullable = false) // id пользователя из auth-service
    private Long userId;

    @Column(columnDefinition = "TEXT") // Произвольная длина текста
    private String text;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // @PrePersist: автоматически ставит метку времени при сохранении нового комментария.
    // Так не нужно вручную вызывать comment.setCreatedAt(LocalDateTime.now()) перед save().
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
