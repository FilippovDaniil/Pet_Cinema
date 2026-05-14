package com.cinema.dto.order; // Пакет для DTO сервиса заказов

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDto {
    // DTO билета — создаётся после успешной оплаты заказа
    // Ticket — физический билет (QR-код), Order — документ об оплате

    private Long id;          // Идентификатор билета
    private Long orderId;     // ID заказа, из которого создан билет
    private Long sessionId;   // ID сеанса
    private Long userId;      // ID покупателя
    private int seatRow;      // Ряд
    private int seatNumber;   // Место
    private String extraServices; // JSON-строка с доп.услугами, например: [{"name":"3D-очки","price":30}]
    private String qrCode;    // QR-код билета (Base64 или UUID-строка для сканирования на входе)
    private String status;    // Статус: "ACTIVE" (не использован), "USED", "CANCELLED"
}
