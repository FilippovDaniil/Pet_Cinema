package com.cinema.order.entity;

// Тип позиции внутри заказа (OrderItem).
// OrderItem — полиморфная сущность: одна и та же таблица хранит и билеты, и еду.
// ItemType определяет, какие поля OrderItem заполнены:
//   TICKET → заполнены: ticketSessionId, ticketSeatRow, ticketSeatNumber, ticketExtraServices
//   FOOD   → заполнены: foodItemId/foodItem, quantity
public enum ItemType {
    TICKET, // Позиция — билет на конкретное место в сеансе
    FOOD    // Позиция — товар из меню кинотеатра
}
