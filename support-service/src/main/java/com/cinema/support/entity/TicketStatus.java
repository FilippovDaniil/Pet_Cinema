package com.cinema.support.entity;

// TicketStatus — перечисление (enum) статусов тикета технической поддержки.
// Важно: это НЕ TicketStatus из order-service (там ACTIVE/USED/CANCELLED для билетов в кино).
// Здесь TICKET — это обращение пользователя в службу поддержки.
// Хранится в БД как строка ("OPEN" / "CLOSED") через @Enumerated(EnumType.STRING).
public enum TicketStatus {

    // OPEN — тикет открыт, вопрос пользователя ещё не решён.
    // Начальный статус при создании тикета (createTicket → status = OPEN).
    // Администратор может просматривать и отвечать на открытые тикеты.
    OPEN,

    // CLOSED — тикет закрыт, вопрос решён.
    // Статус устанавливается администратором через PATCH /api/support/tickets/{id}/close.
    // После закрытия тикет остаётся в БД для истории.
    CLOSED
}
