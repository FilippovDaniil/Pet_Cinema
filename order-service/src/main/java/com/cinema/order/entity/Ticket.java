package com.cinema.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// @Entity — Hibernate управляет этим классом, таблица "tickets"
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID заказа, по которому выдан билет (в этой же БД — можно было бы @ManyToOne,
    // но здесь достаточно scalar, т.к. мы не навигируемся Order→Ticket из этого класса).
    @Column(nullable = false)
    private Long orderId;

    // ID сеанса из hall-service. Long — межсервисная изоляция (нет FK на hall_db).
    // order-service использует это значение для вызова lb://hall-service/api/sessions/{id}.
    @Column(nullable = false)
    private Long sessionId;

    // ID пользователя из auth-service. Long — межсервисная изоляция.
    @Column(nullable = false)
    private Long userId;

    // Ряд места в зале (1-based). Нужен для физического обозначения на билете.
    private int seatRow;

    // Номер места в ряду (1-based).
    private int seatNumber;

    // JSON-строка с выбранными дополнительными услугами.
    // columnDefinition="TEXT" — неограниченная длина строки в PostgreSQL.
    // Пример: ["Вибрация кресла", "Персональный официант"]
    @Column(columnDefinition = "TEXT")
    private String extraServices;

    // QR-код билета — строка для сканирования на входе.
    // length=512 — QR-коды могут быть длинными (URL + параметры + подпись).
    @Column(length = 512)
    private String qrCode;

    // Текущий статус билета: ACTIVE / USED / CANCELLED.
    // EnumType.STRING — хранится строковое имя константы, безопасно при изменениях enum.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;
}
