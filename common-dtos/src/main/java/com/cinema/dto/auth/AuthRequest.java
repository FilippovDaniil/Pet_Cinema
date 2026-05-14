package com.cinema.dto.auth; // Пакет для DTO аутентификации — всё что связано с входом/регистрацией

import jakarta.validation.constraints.NotBlank; // Аннотация валидации: поле не должно быть null, пустым или состоять из пробелов
import lombok.AllArgsConstructor; // Lombok: генерирует конструктор со ВСЕМИ полями
import lombok.Builder;            // Lombok: генерирует паттерн Builder для удобного создания объектов
import lombok.Data;               // Lombok: генерирует геттеры, сеттеры, equals, hashCode, toString
import lombok.NoArgsConstructor;  // Lombok: генерирует пустой конструктор (нужен для Jackson-десериализации JSON)

@Data            // = @Getter + @Setter + @EqualsAndHashCode + @ToString — всё сразу
@Builder         // Позволяет писать: AuthRequest.builder().username("user").password("pass").build()
@NoArgsConstructor  // new AuthRequest() — нужен Spring/Jackson при парсинге тела запроса
@AllArgsConstructor // new AuthRequest("user", "pass") — нужен @Builder для внутренней работы
public class AuthRequest {

    @NotBlank(message = "Username must not be blank") // Валидация: при @Valid в контроллере Spring вернёт 400 если поле пустое
    private String username; // Логин пользователя

    @NotBlank(message = "Password must not be blank") // Аналогичная валидация для пароля
    private String password; // Пароль пользователя (в открытом виде — сервис сам сравнит с BCrypt-хешем)
}
