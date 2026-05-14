package com.cinema.movie.repository;

import com.cinema.movie.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// @Repository — маркер Spring: создаёт бин и оборачивает JPA-исключения в Spring DataAccessException.
// JpaRepository<Genre, Long>:
//   - Genre — тип сущности
//   - Long  — тип первичного ключа
//   Из коробки: findAll(), findById(), save(), delete(), count(), existsById() и другие
@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {

    // Spring Data автоматически генерирует запрос по имени метода:
    // findByName(name) → SELECT * FROM genres WHERE name = ?
    // Возвращает Optional: если жанр не найден — Optional.empty(), а не null или исключение.
    // Используется в GenreService.createGenre() для проверки дублирования,
    // и в DataLoader.run() при создании начальных данных.
    Optional<Genre> findByName(String name);
}
