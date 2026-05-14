package com.cinema.dto.support; // Пакет для DTO сервиса поддержки

import jakarta.validation.constraints.NotNull; // Значение не null
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignAdminRequest {
    // Запрос на назначение администратора к тикету (ROLE_ADMIN → POST /api/support/tickets/{id}/assign)

    @NotNull(message = "Admin ID must not be null")
    private Long adminId; // ID администратора, который берёт тикет в работу
}
