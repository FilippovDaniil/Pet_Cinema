package com.cinema.movie.repository;

import com.cinema.movie.entity.Movie;
import com.cinema.movie.entity.MovieType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    // JPQL-запрос (Java Persistence Query Language) — оперирует именами классов и полей Java,
    // а не именами таблиц и колонок SQL. Hibernate транслирует его в SQL автоматически.
    //
    // SELECT DISTINCT m    — DISTINCT нужен из-за JOIN: фильм с несколькими жанрами
    //                        без DISTINCT дублировался бы в результатах.
    // FROM Movie m         — из сущности Movie (таблица movies)
    // LEFT JOIN m.genres g — LEFT JOIN: фильмы БЕЗ жанров тоже попадают в результат.
    //                        INNER JOIN исключил бы фильмы без жанров.
    //
    // Условия с NULL-трюком:
    //   :genre IS NULL OR g.name = :genre
    //   — если genre = null (фильтр не задан) → условие всегда true → жанр не фильтруется
    //   — если genre = "Action"               → берём только фильмы с жанром Action
    // То же самое для type и durationMax.
    // Это позволяет один метод использовать для любой комбинации фильтров.
    //
    // Pageable pageable — Spring Data передаёт сюда параметры пагинации (LIMIT / OFFSET)
    // и сортировки. Метод возвращает Page<Movie> — содержит данные + метаданные (totalElements, totalPages).
    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN m.genres g WHERE " +
           "(:genre IS NULL OR g.name = :genre) AND " +
           "(:type IS NULL OR m.type = :type) AND " +
           "(:durationMax IS NULL OR m.durationMinutes <= :durationMax)")
    Page<Movie> findAllWithFilters(
            @Param("genre") String genre,           // null = фильтр отключён
            @Param("type") MovieType type,          // null = фильтр отключён
            @Param("durationMax") Integer durationMax, // null = фильтр отключён
            Pageable pageable                        // страница + размер
    );
}
