package com.cinema.dto.notification; // Пакет для DTO уведомлений

import com.fasterxml.jackson.annotation.JsonFormat; // Аннотация Jackson: задаёт формат сериализации даты в JSON
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; // Дата и время без часового пояса

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private Long id;       // Идентификатор уведомления в БД
    private Long userId;   // ID пользователя — владельца уведомления
    private String title;  // Заголовок уведомления, например "Билет куплен"
    private String content; // Текст уведомления, например "Вы купили билет на Интерстеллар, 18:00"
    private boolean read;  // false = непрочитанное (показывается как "новое"), true = прочитанное

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // Сериализует дату как строку "2025-06-01 18:00:00" вместо массива [2025,6,1,18,0,0]
    private LocalDateTime createdAt; // Время создания уведомления
}
