package com.cinema.dto.hall; // Пакет для DTO сервиса залов

import jakarta.validation.constraints.DecimalMin; // Валидация: число должно быть >= или > заданного значения
import jakarta.validation.constraints.NotBlank;   // Валидация: строка не null и не пустая
import jakarta.validation.constraints.NotNull;    // Валидация: значение не должно быть null
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Точный тип для денежных сумм

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtraServiceCreateRequest {
    // Запрос на создание дополнительной услуги (ADMIN → POST /api/halls/{id}/extra-services)

    @NotBlank(message = "Service name must not be blank")
    private String name; // Название услуги, например "Персональный официант"

    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    // inclusive = false означает строго больше 0 (т.е. 0.0 не допустимо)
    private BigDecimal price; // Цена услуги (например: new BigDecimal("50.00"))
}
