package com.cinema.support.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor — стандартный Lombok набор.
// @NoArgsConstructor обязателен для JPA (Hibernate использует конструктор без аргументов).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// @Entity + @Table — отображение класса на таблицу "support_messages" в PostgreSQL.
@Entity
@Table(name = "support_messages")
public class SupportMessage {

    // @Id + @GeneratedValue(IDENTITY) — автоинкрементный первичный ключ.
    // PostgreSQL генерирует id при INSERT.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ================================================================
    // КРИТИЧЕСКИЙ ПАТТЕРН: ДУБЛИРУЮЩАЯСЯ КОЛОНКА "ticket_id"
    // ================================================================
    // Проблема: Hibernate видит ДВА маппинга на одну колонку "ticket_id":
    //   1. скалярное поле ticketId (@Column)
    //   2. навигационное поле ticket (@JoinColumn)
    // Без явных настроек Hibernate создаёт ДВА логических имени для одной физической колонки
    // и падает с DuplicateMappingException при старте.
    //
    // Решение: поделить ответственность:
    //   - ticketId — ПИШЕТ в колонку (insertable=true, updatable=true по умолчанию)
    //   - ticket   — ТОЛЬКО ЧИТАЕТ (insertable=false, updatable=false — не трогает колонку)
    //
    // Это тот же паттерн что в order-service/OrderItem.java (food_item_id).

    // ticketId — Long: управляет физической записью в колонку "ticket_id".
    // Используется в Builder: .ticketId(savedTicket.getId()).
    // NOT NULL: каждое сообщение обязано принадлежать тикету.
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;   // управляет INSERT/UPDATE колонки ticket_id

    // ticket — навигационное свойство для JPA join (чтение SupportTicket объекта).
    // LAZY: SupportTicket загружается из БД только при явном вызове getMessage.getTicket().
    // insertable=false, updatable=false — Hibernate НЕ включает это поле в INSERT/UPDATE.
    // Это предотвращает DuplicateMappingException: оба маппинга указывают на "ticket_id",
    // но только один (ticketId) реально пишет.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", insertable = false, updatable = false)
    private SupportTicket ticket;   // read-only навигация к родительскому тикету

    // senderId — id пользователя (из auth-service), отправившего сообщение.
    // Может быть как clientId так и adminId — кто угодно с доступом к тикету.
    @Column(nullable = false)
    private Long senderId;

    // content — текст сообщения.
    // columnDefinition = "TEXT" — PostgreSQL тип TEXT (до 1 ГБ), не VARCHAR(255).
    // Используем TEXT так как сообщения могут быть длинными.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // sentAt — время отправки сообщения.
    // NOT NULL: устанавливается в @PrePersist если не задано явно.
    @Column(nullable = false)
    private LocalDateTime sentAt;

    // @PrePersist — вызывается ПЕРЕД первым INSERT.
    // Если sentAt не задан в Builder, устанавливается текущее время.
    // В DataLoader sentAt задаётся явно (now, now.plusMinutes(5)),
    // поэтому @PrePersist его не перезаписывает (if null).
    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}
