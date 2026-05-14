package com.cinema.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// @Data — Lombok: геттеры, сеттеры, toString, equals, hashCode.
@Data
// @Builder — Lombok: паттерн Builder для создания объектов.
@Builder
// @NoArgsConstructor — обязателен для JPA (Hibernate создаёт через конструктор без аргументов).
@NoArgsConstructor
// @AllArgsConstructor — нужен для @Builder в паре с @NoArgsConstructor.
@AllArgsConstructor
// @Entity — JPA: этот класс отображается на таблицу в PostgreSQL.
@Entity
// @Table(name = "notifications") — явное имя таблицы.
@Table(name = "notifications")
public class Notification {

    // @Id + @GeneratedValue(IDENTITY) — автоинкрементный первичный ключ (PostgreSQL SERIAL).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // userId — id пользователя которому адресовано уведомление.
    // Хранится как Long (нет FK на auth-service — микросервисная изоляция).
    // NOT NULL: каждое уведомление должно иметь получателя.
    @Column(nullable = false)
    private Long userId;

    // title — заголовок уведомления (краткое описание).
    // Примеры: "Билет куплен!", "Новое сообщение в поддержке"
    // NOT NULL: уведомление без заголовка не имеет смысла.
    @Column(nullable = false)
    private String title;

    // content — текст уведомления (подробности).
    // columnDefinition = "TEXT" — PostgreSQL тип TEXT (без ограничения длины VARCHAR).
    // Может содержать детали: название фильма, время сеанса, сумму.
    @Column(columnDefinition = "TEXT")
    private String content;

    // read — флаг прочитанности уведомления.
    // boolean (primitive, не Boolean wrapper): не может быть null.
    // = false — значение по умолчанию: новые уведомления всегда непрочитанные.
    // Пользователь отмечает как прочитанное через PATCH /api/notifications/{id}/read.
    @Column(nullable = false)
    private boolean read = false;

    // @CreationTimestamp — Hibernate аннотация: автоматически устанавливает текущее время
    //   при первом INSERT. Аналог @PrePersist { createdAt = LocalDateTime.now() }.
    //   Удобнее @PrePersist: не нужно писать метод lifecycle hook.
    // updatable = false — после создания поле НЕ обновляется (время создания неизменно).
    // NOT NULL: гарантируется @CreationTimestamp.
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
