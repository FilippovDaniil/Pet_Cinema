package com.cinema.movie.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // movieId и userId хранятся как простые Long, а не через @ManyToOne @JoinColumn.
    // Это осознанный выбор микросервисной архитектуры:
    //   - userId принадлежит auth-service (другая БД) — мы не можем создать FK между разными БД
    //   - movieId — id записи в ЭТОЙ же таблице movies, но связь нам не нужна объектно:
    //     отзывы запрашиваются по movieId отдельным запросом, а не через JOIN
    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "user_id", nullable = false)
    private Long userId;    // id пользователя из auth-service

    @Column(nullable = false)
    private int rating;     // Оценка от 1 до 5 (валидация в ReviewCreateRequest — @Min(1) @Max(5))

    @Column(columnDefinition = "TEXT") // TEXT: комментарий может быть длинным
    private String comment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // @PrePersist: JPA устанавливает createdAt перед INSERT. Не нужен @PreUpdate —
    // дата создания не должна меняться при обновлении.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
