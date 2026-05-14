package com.cinema.order.entity;

// Статус жизненного цикла заказа.
// Переходы состояний:
//   PENDING → PAID     (при успешной оплате через webhook от payment-simulator)
//   PENDING → CANCELLED (при отмене или ошибке оплаты)
//   Заказы продавца и заказы на еду создаются сразу в статусе PAID (оплата считается мгновенной).
public enum OrderStatus {
    PENDING,   // Заказ создан, ожидает оплаты (Payment Request отправлен в Kafka)
    PAID,      // Оплата подтверждена, билет/еда выданы
    CANCELLED  // Заказ отменён (оплата не прошла или пользователь отменил)
}
