package com.cinema.dto.auth; // Пакет для DTO аутентификации

import jakarta.validation.constraints.Email;    // Валидация: строка должна быть валидным email-адресом (foo@bar.com)
import jakarta.validation.constraints.NotBlank; // Валидация: не null, не пустая, не только пробелы
import jakarta.validation.constraints.Size;     // Валидация: ограничение длины строки (min/max символов)
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Username must not be blank")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters") // Логин: от 3 до 50 символов
    private String username; // Логин пользователя (уникальный в БД)

    @NotBlank(message = "Email must not be blank")
    @Email(message = "Email must be a valid email address") // Проверяет формат email через regex
    private String email; // Email пользователя (уникальный в БД)

    @NotBlank(message = "Password must not be blank")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters") // Пароль: минимум 6 символов
    private String password; // Пароль в открытом виде — auth-service сохранит BCrypt-хеш
}
