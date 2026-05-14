package com.cinema.auth.repository; // Пакет репозиториев auth-service

import com.cinema.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository; // Базовый интерфейс Spring Data JPA

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // JpaRepository<User, Long> — предоставляет готовые методы:
    // save(), findById(), findAll(), deleteById(), existsById(), count() и т.д.
    // Long — тип первичного ключа

    Optional<User> findByUsername(String username);
    // Spring Data генерирует SQL: SELECT * FROM users WHERE username = ?
    // Используется при логине (UserDetailsService) и в AuthService

    Optional<User> findByEmail(String email);
    // SELECT * FROM users WHERE email = ?
    // Используется для проверки дублирования email при регистрации

    boolean existsByUsername(String username);
    // SELECT EXISTS(SELECT 1 FROM users WHERE username = ?)
    // Быстрая проверка занятости логина (без загрузки всего объекта)

    boolean existsByEmail(String email);
    // Аналогично — быстрая проверка для email
}
