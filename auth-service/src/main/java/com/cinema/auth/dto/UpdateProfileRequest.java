package com.cinema.auth.dto; // Пакет DTO для auth-service (локальные DTO, не в common-dtos)

import lombok.Data;

@Data // Генерирует геттеры, сеттеры, equals, hashCode, toString
public class UpdateProfileRequest {
    // Тело запроса PATCH /api/auth/me — обновление профиля пользователя.
    // Все поля опциональны (нет @NotBlank) — обновляем только то, что передано.

    private String username; // Новый логин (null = не менять)
    private String email;    // Новый email (null = не менять)
}
