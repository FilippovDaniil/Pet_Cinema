package com.cinema.hall.repository;

import com.cinema.hall.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    // Метод по соглашению об именовании Spring Data JPA:
    //   findBy          → SELECT ... WHERE
    //   MovieId         → movie_id = ?
    //   And             → AND
    //   Active          → active = ?
    //
    // Генерируемый SQL: SELECT * FROM sessions WHERE movie_id = ? AND active = ?
    // Используется для получения активных сеансов конкретного фильма.
    List<Session> findByMovieIdAndActive(Long movieId, boolean active);

    // Аналогично: SELECT * FROM sessions WHERE hall_id = ? AND active = ?
    // Используется для получения активных сеансов в конкретном зале.
    List<Session> findByHallIdAndActive(Long hallId, boolean active);

    // Кастомный JPQL-запрос с опциональными фильтрами через NULL-трюк.
    //
    // JPQL (Java Persistence Query Language) — аналог SQL, но оперирует именами классов и полей
    // (Session, session.movieId), а не именами таблиц и колонок (sessions, movie_id).
    //
    // Паттерн (:param IS NULL OR s.field = :param):
    //   Если параметр null  → условие всегда true (фильтр не применяется)
    //   Если параметр задан → фильтрует по значению
    // Это позволяет один запрос использовать для любой комбинации фильтров.
    //
    // s.hall.id — обращение к полю id связанной сущности Hall через навигацию (не по имени колонки).
    //
    // from / to фильтруют по startTime: сеансы, начинающиеся в указанном промежутке.
    //
    // Примечание: этот запрос возвращает ВСЕ сеансы (включая inactive),
    // поэтому SessionService дополнительно фильтрует по active в Java.
    @Query("SELECT s FROM Session s WHERE " +
           "(:movieId IS NULL OR s.movieId = :movieId) AND " +
           "(:hallId IS NULL OR s.hall.id = :hallId) AND " +
           "(:from IS NULL OR s.startTime >= :from) AND " +
           "(:to IS NULL OR s.startTime <= :to)")
    List<Session> findByFilters(
            @Param("movieId") Long movieId,   // null = не фильтровать по фильму
            @Param("hallId") Long hallId,     // null = не фильтровать по залу
            @Param("from") LocalDateTime from, // null = нет нижней границы времени
            @Param("to") LocalDateTime to      // null = нет верхней границы времени
    );
}
