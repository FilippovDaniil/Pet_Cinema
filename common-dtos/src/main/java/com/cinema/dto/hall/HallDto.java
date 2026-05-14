package com.cinema.dto.hall; // Пакет для DTO сервиса залов

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HallDto {

    private Long id;          // Идентификатор зала в БД
    private String name;      // Название зала, например "Зал VIP" или "Зал 3D"
    private String type;      // Тип зала: "NORMAL", "VIP", "THREE_D", "FIVE_D" — строка, не enum
    private int rowsCount;    // Количество рядов в зале
    private int seatsPerRow;  // Количество мест в каждом ряду
    private String description; // Описание зала (опционально, может быть null)
}
