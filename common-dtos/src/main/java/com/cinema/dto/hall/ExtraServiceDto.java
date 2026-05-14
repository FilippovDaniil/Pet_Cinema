package com.cinema.dto.hall; // Пакет для DTO сервиса залов

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Точный тип для денежных сумм

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtraServiceDto {
    // DTO дополнительной услуги зала (например "Вибрация кресла", "3D-очки премиум")

    private Long id;           // Идентификатор услуги в БД
    private Long hallId;       // ID зала, к которому привязана услуга
    private String name;       // Название услуги
    private BigDecimal price;  // Цена услуги в рублях (добавляется к стоимости билета)
}
