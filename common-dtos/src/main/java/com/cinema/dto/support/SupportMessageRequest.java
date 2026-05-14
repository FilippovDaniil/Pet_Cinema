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
public class SupportMessageRequest {
    // Запрос на отправку сообщения в тикет (клиент или admin → POST /api/support/tickets/{id}/messages)

    @NotBlank(message = "Content must not be blank")
    private String content; // Текст сообщения — обязательное поле
}
