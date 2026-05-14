package com.cinema.dto.auth; // Пакет для DTO аутентификации

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;       // Идентификатор пользователя в БД (автоинкремент)
    private String username; // Логин пользователя
    private String email;    // Email пользователя
    private String role;     // Роль: "ROLE_CLIENT", "ROLE_SELLER" или "ROLE_ADMIN"
                             // Строка, а не enum — чтобы сервисы не зависели от конкретного enum-класса
}
