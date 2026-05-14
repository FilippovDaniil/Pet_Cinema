package com.cinema.movie.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data              // Lombok: геттеры, сеттеры, equals, hashCode, toString
@Builder           // Lombok: паттерн строитель
@NoArgsConstructor // Lombok: пустой конструктор для Hibernate
@AllArgsConstructor// Lombok: конструктор со всеми полями для @Builder
@Entity            // JPA: класс является таблицей
@Table(name = "movies") // Явное имя таблицы "movies"
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // PostgreSQL SERIAL — автоинкремент
    private Long id;

    @Column(nullable = false, length = 255) // Обязательное поле, VARCHAR(255)
    private String title;

    @Column(columnDefinition = "TEXT")
    // TEXT вместо VARCHAR: длинное описание без лимита в 255 символов
    private String description;

    @Column(name = "poster_url") // snake_case имя колонки; без имени Hibernate создал бы "poster_url" автоматически,
    // но явное указание делает маппинг очевидным при чтении
    private String posterUrl;

    @Column(name = "duration_minutes", nullable = false) // Продолжительность в минутах, обязательное поле
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    // STRING: в БД хранится "TWO_D" / "THREE_D" / "FIVE_D", а не 0 / 1 / 2.
    // ORDINAL (по умолчанию) опасен: если поменять порядок enum — данные сломаются.
    @Column(nullable = false)
    private MovieType type;

    // @ManyToMany: фильм может принадлежать многим жанрам, жанр — у многих фильмов.
    // В БД это реализуется через промежуточную таблицу movie_genres(movie_id, genre_id).
    @ManyToMany(fetch = FetchType.LAZY)
    // LAZY: жанры не загружаются из БД автоматически при загрузке фильма.
    // Загрузка происходит только при первом обращении к genres (внутри @Transactional).
    // EAGER (альтернатива) загружает всегда — лишние запросы, если жанры не нужны.
    @JoinTable(
            name = "movie_genres",          // Имя связующей таблицы
            joinColumns = @JoinColumn(name = "movie_id"),         // Колонка с id фильма
            inverseJoinColumns = @JoinColumn(name = "genre_id")   // Колонка с id жанра
    )
    @Builder.Default
    // @Builder.Default: без этой аннотации @Builder инициализирует поле как null,
    // игнорируя инициализатор "= new ArrayList<>()". С аннотацией builder использует дефолтное значение.
    private List<Genre> genres = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt; // Когда запись создана

    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // Когда запись последний раз обновлялась

    // @PrePersist — JPA lifecycle hook: вызывается Hibernate перед INSERT в БД.
    // Устанавливаем оба временных поля при создании.
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // @PreUpdate — JPA lifecycle hook: вызывается Hibernate перед UPDATE в БД.
    // Обновляем только updatedAt; createdAt остаётся неизменным.
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
