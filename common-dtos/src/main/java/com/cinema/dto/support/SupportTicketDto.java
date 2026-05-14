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
public class SupportTicketDto {
    // DTO тикета (обращения) в службу поддержки

    private Long id;          // Идентификатор тикета
    private Long clientId;    // ID клиента, открывшего тикет
    private Long adminId;     // ID администратора, взявшего тикет (null пока не назначен)
    private String subject;   // Тема обращения, например "Помогите с оформлением заказа"
    private String status;    // Статус: "OPEN" (открыт) или "CLOSED" (закрыт)

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt; // Время создания тикета

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt; // Время последнего обновления (при каждом сообщении)
}
