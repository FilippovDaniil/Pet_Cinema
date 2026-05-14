package com.cinema.dto.hall; // Пакет для DTO сервиса залов

import com.fasterxml.jackson.annotation.JsonFormat; // Формат даты при десериализации из JSON
import jakarta.validation.constraints.DecimalMin;    // Цена строго > 0
import jakarta.validation.constraints.NotNull;       // Поле обязательно
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionCreateRequest {
    // Запрос на создание нового сеанса (ADMIN → POST /api/sessions)

    @NotNull(message = "Movie ID must not be null")
    private Long movieId; // ID фильма из movie-service (без FK — просто Long)

    @NotNull(message = "Hall ID must not be null")
    private Long hallId; // ID зала из hall-service

    @NotNull(message = "Start time must not be null")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // JSON "2025-06-01 18:00:00" → LocalDateTime
    private LocalDateTime startTime; // Время начала сеанса

    @NotNull(message = "End time must not be null")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime; // Время окончания (обычно startTime + длительность фильма)

    @NotNull(message = "Base price must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    private BigDecimal basePrice; // Базовая цена билета на этот сеанс
}
