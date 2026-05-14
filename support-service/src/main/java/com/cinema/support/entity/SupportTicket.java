package com.cinema.support.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// @Data — Lombok генерирует: геттеры и сеттеры для всех полей, toString(), equals(), hashCode().
@Data
// @Builder — Lombok добавляет статический inner-класс Builder.
// Позволяет создавать объекты цепочкой: SupportTicket.builder().clientId(1L).subject("...").build()
@Builder
// @NoArgsConstructor — конструктор без аргументов. ОБЯЗАТЕЛЕН для JPA:
//   Hibernate создаёт объекты через new SupportTicket() при чтении из БД.
@NoArgsConstructor
// @AllArgsConstructor — конструктор со всеми полями. Нужен для @Builder (Lombok сочетание).
@AllArgsConstructor
// @Entity — JPA аннотация: этот класс отображается на таблицу в PostgreSQL.
@Entity
// @Table(name = "support_tickets") — явно задаём имя таблицы.
// Без этой аннотации Hibernate использовал бы "support_ticket" (без 's').
@Table(name = "support_tickets")
public class SupportTicket {

    // @Id — первичный ключ таблицы.
    @Id
    // @GeneratedValue(IDENTITY) — значение генерирует PostgreSQL при INSERT (SERIAL/BIGSERIAL).
    // Hibernate не вычисляет id сам — ждёт результата от БД.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // clientId — id пользователя (из auth-service), создавшего тикет.
    // NOT NULL: каждый тикет обязан иметь владельца.
    // Хранится как Long (не FK) — межсервисная изоляция: нет FK на auth-service.
    @Column(nullable = false)
    private Long clientId;

    // adminId — id администратора, назначенного на тикет.
    // NULLABLE: при создании тикета администратор ещё не назначен (adminId = null).
    // Назначается через PUT /api/support/tickets/{id}/assign.
    private Long adminId;

    // subject — тема/заголовок обращения в поддержку.
    // NOT NULL: каждый тикет должен иметь описание проблемы.
    @Column(nullable = false)
    private String subject;

    // @Enumerated(STRING) — сохраняет Enum как строку ("OPEN" или "CLOSED"), не как число.
    // Строковый вариант безопаснее: добавление нового значения в enum не нарушает существующие данные.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;  // OPEN при создании, CLOSED после решения

    // createdAt — момент создания тикета.
    // NOT NULL: устанавливается в @PrePersist перед первым сохранением.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // updatedAt — момент последнего изменения тикета (отправка сообщения, смена статуса).
    // NOT NULL: устанавливается в @PrePersist и обновляется в @PreUpdate.
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // @PrePersist — JPA lifecycle callback: вызывается ПЕРЕД первым INSERT в БД.
    // Если поля createdAt/updatedAt не были установлены явно (через Builder), они заполняются здесь.
    // Проверка "if null" позволяет переопределить значение вручную (например, в DataLoader).
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;   // только если не задано явно
        if (updatedAt == null) updatedAt = now;   // только если не задано явно
    }

    // @PreUpdate — JPA lifecycle callback: вызывается ПЕРЕД каждым UPDATE.
    // Автоматически актуализирует updatedAt при каждом изменении сущности.
    // В SupportService явно делают ticket.setUpdatedAt(now) + save() — это тоже работает,
    // но @PreUpdate — дополнительная защита на случай прямого вызова repository.save().
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
