package com.cinema.dto.auth; // Пакет для DTO аутентификации

import jakarta.validation.constraints.NotBlank; // Валидация: не null, не пустая строка
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    @NotBlank(message = "Refresh token must not be blank")
    private String refreshToken; // Refresh-токен, полученный при логине — отправляется на POST /api/auth/refresh
                                 // Сервис проверит его в БД и Redis-blacklist, выдаст новую пару токенов
}
