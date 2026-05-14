package com.cinema.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Lombok: геттеры/сеттеры + equals/hashCode/toString
@Data
// Lombok: паттерн Builder для удобного создания сложных объектов Order
@Builder
// Lombok: конструктор без аргументов — обязателен для JPA
@NoArgsConstructor
// Lombok: конструктор со всеми полями
@AllArgsConstructor
// @Entity — Hibernate управляет этим классом
@Entity
// Таблица "orders" (не "order" — "order" зарезервированное слово в SQL!)
@Table(name = "orders")
public class Order {

    // Суррогатный первичный ключ, автогенерация PostgreSQL
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID пользователя из auth-service. Long (не @ManyToOne) — межсервисная изоляция:
    // order-service не имеет FK на auth_db, хранит только числовой идентификатор.
    @Column(nullable = false)
    private Long userId;

    // ID продавца — заполняется только при заказе через endpoint /ticket/by-seller.
    // null = клиентский заказ (через онлайн-форму), не null = кассовый заказ.
    private Long sellerId;

    // Тип заказа: TICKET / FOOD / MIXED.
    // EnumType.STRING — безопасное хранение.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    // Текущий статус: PENDING → PAID или PENDING → CANCELLED.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // Итоговая сумма заказа. Рассчитывается в OrderService при создании.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    // Время создания заказа. Устанавливается автоматически в @PrePersist.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Позиции заказа (билеты и/или еда).
    // cascade=ALL — при сохранении/удалении Order автоматически сохраняются/удаляются OrderItem.
    // fetch=EAGER — позиции загружаются сразу вместе с заказом (нужно для формирования OrderDto).
    // mappedBy="order" — "order" — это поле в классе OrderItem с @ManyToOne.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    // @Builder.Default — без этого Lombok @Builder инициализирует items как null,
    // что приводит к NullPointerException при вызове items.add() / items.size().
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    // JPA lifecycle callback: вызывается Hibernate перед первым INSERT.
    // Устанавливает createdAt если не задано вручную (защита от null в тестах/прямом создании).
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
