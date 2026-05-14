package com.cinema.hall.repository;

import com.cinema.hall.entity.Hall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// @Repository — маркерная аннотация Spring. Регистрирует интерфейс как компонент Spring Context.
// Также преобразует SQL-исключения (SQLException) в Spring DataAccessException — единый иерархия исключений.
@Repository
// JpaRepository<Hall, Long> — Spring Data JPA автоматически генерирует реализацию этого интерфейса.
// Параметры: Hall — тип сущности, Long — тип первичного ключа.
//
// Из коробки доступны:
//   save(Hall)              — INSERT или UPDATE
//   findById(Long)          — SELECT WHERE id = ?
//   findAll()               — SELECT * FROM halls
//   existsById(Long)        — SELECT COUNT(*) > 0 WHERE id = ?
//   delete(Hall)            — DELETE FROM halls WHERE id = ?
//   count()                 — SELECT COUNT(*) FROM halls
//
// Hall не требует дополнительных запросов сверх стандартного набора JpaRepository,
// поэтому кастомных методов здесь нет.
public interface HallRepository extends JpaRepository<Hall, Long> {
}
