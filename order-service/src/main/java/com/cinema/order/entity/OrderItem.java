package com.cinema.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// @Entity — Hibernate управляет этим классом, маппит на таблицу order_items
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ссылка на родительский заказ.
    // FetchType.LAZY — не загружаем Order при каждом чтении OrderItem (избегаем N+1).
    // @JoinColumn(name="order_id") — физическая колонка order_id в таблице order_items.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Тип позиции: TICKET (билет) или FOOD (еда).
    // Определяет, какие из следующих полей заполнены.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;

    // ---- Поля для позиции-билета (заполнены только если itemType = TICKET) ----

    // ID сеанса из hall-service (хранится как Long — межсервисная изоляция)
    private Long ticketSessionId;

    // Ряд места в зале (1-based)
    private int ticketSeatRow;

    // Номер места в ряду (1-based)
    private int ticketSeatNumber;

    // JSON-строка с выбранными доп.услугами: ["3D-очки", "Вибрация кресла"].
    // columnDefinition="TEXT" — неограниченная длина, т.к. услуг может быть много.
    @Column(columnDefinition = "TEXT")
    private String ticketExtraServices;

    // ---- Поля для позиции-еды (заполнены только если itemType = FOOD) ----

    // КРИТИЧЕСКИЙ ПАТТЕРН: "Дублирующая колонка" — два поля ссылаются на одну физическую колонку.
    //
    // Проблема: Hibernate не позволяет иметь два логических маппинга на одну и ту же физическую
    // колонку без явного указания. Если оба @Column и @JoinColumn ссылаются на "food_item_id"
    // без insertable/updatable=false, Hibernate падает с DuplicateMappingException.
    //
    // Решение: скалярное поле foodItemId управляет INSERT/UPDATE (insertable=true, updatable=true),
    // навигационное поле foodItem только читает (insertable=false, updatable=false).

    // Скалярный FK: хранит числовой ID товара. Управляет колонкой food_item_id в БД.
    @Column(name = "food_item_id")
    private Long foodItemId;

    // Навигационное поле: позволяет напрямую обращаться к объекту FoodItem.
    // insertable=false, updatable=false — Hibernate игнорирует это поле при INSERT/UPDATE,
    // за запись отвечает foodItemId выше. Только для чтения (JOIN при SELECT).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", insertable = false, updatable = false)
    private FoodItem foodItem;

    // Количество единиц товара (для FOOD). Для TICKET всегда 1.
    private int quantity;

    // Цена позиции на момент заказа. Фиксируется при создании — не изменится при изменении прайса.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
