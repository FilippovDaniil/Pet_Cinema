package com.cinema.dto.hall; // Пакет для DTO сервиса залов

import com.fasterxml.jackson.annotation.JsonFormat; // Формат даты в JSON
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;   // Точный тип для денег
import java.time.LocalDateTime; // Дата и время без часового пояса

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionDto {
    // DTO кинносеанса. Обратите внимание: нет поля hallName — только hallId
    // Фронтенд при необходимости запрашивает детали зала отдельно

    private Long id;        // Идентификатор сеанса в БД
    private Long movieId;   // ID фильма (НЕ FK к movie-service — микросервисная изоляция!)
    private Long hallId;    // ID зала, в котором проходит сеанс

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime; // Время начала сеанса

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;   // Время окончания сеанса

    private BigDecimal basePrice; // Базовая цена билета (без дополнительных услуг)
    private boolean active;       // true = сеанс активен, false = отменён или прошёл
}
