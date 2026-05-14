package com.cinema.dto.common; // Пакет для общих DTO — используются во всех сервисах

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String errorCode; // Машиночитаемый код ошибки, например "NOT_FOUND", "UNAUTHORIZED"
                              // Фронтенд использует его для локализации сообщений
    private String message;   // Человекочитаемое описание ошибки, например "Movie with id 5 not found"
                              // Возвращается клиенту в теле ответа при статусах 4xx/5xx
}
