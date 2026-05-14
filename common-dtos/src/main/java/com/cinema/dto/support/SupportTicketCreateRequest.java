package com.cinema.dto.support; // Пакет для DTO сервиса поддержки

import jakarta.validation.constraints.NotBlank; // Строка не null и не пустая
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportTicketCreateRequest {
    // Запрос на открытие нового тикета (ROLE_CLIENT → POST /api/support/tickets)

    @NotBlank(message = "Subject must not be blank")
    private String subject; // Тема обращения — обязательное поле
}
