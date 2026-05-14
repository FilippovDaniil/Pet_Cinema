package com.cinema.dto.hall; // Пакет для DTO сервиса залов

import jakarta.validation.constraints.NotBlank; // Валидация: не null и не пустая строка
import jakarta.validation.constraints.Positive;  // Валидация: число должно быть строго положительным (> 0)
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HallCreateRequest {

    @NotBlank(message = "Hall name must not be blank")
    private String name; // Название нового зала

    @NotBlank(message = "Hall type must not be blank")
    private String type; // Тип зала: "NORMAL", "VIP", "THREE_D", "FIVE_D"

    @Positive(message = "Rows count must be a positive number")
    private int rowsCount; // Количество рядов (целое число > 0)

    @Positive(message = "Seats per row must be a positive number")
    private int seatsPerRow; // Количество мест в ряду (целое число > 0)

    private String description; // Описание зала — необязательное поле, нет валидации
}
