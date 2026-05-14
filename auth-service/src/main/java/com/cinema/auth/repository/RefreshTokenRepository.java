package com.cinema.auth.repository; // Пакет репозиториев auth-service

import com.cinema.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);
    // SELECT * FROM refresh_tokens WHERE token = ?
    // Используется при refresh и logout: ищем запись по значению токена

    void deleteAllByUserId(Long userId);
    // DELETE FROM refresh_tokens WHERE user_id = ?
    // Опциональная операция "выйти со всех устройств" — удаляет все токены пользователя
}
