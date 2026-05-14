package com.cinema.dto.support; // Пакет для DTO сервиса поддержки

import com.fasterxml.jackson.annotation.JsonFormat; // Формат даты в JSON
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportMessageDto {
    // DTO одного сообщения в чате поддержки

    private Long id;        // Идентификатор сообщения
    private Long ticketId;  // ID тикета, в котором отправлено сообщение
    private Long senderId;  // ID отправителя (клиент или admin)
    private String content; // Текст сообщения

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sentAt; // Время отправки сообщения
}
