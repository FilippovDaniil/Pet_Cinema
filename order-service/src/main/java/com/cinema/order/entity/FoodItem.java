package com.cinema.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Lombok: геттеры/сеттеры + equals/hashCode/toString
@Data
// Lombok: Builder.builder()...build() для удобного создания объектов
@Builder
// Lombok: конструктор без аргументов — требуется JPA (Hibernate создаёт сущности через рефлексию)
@NoArgsConstructor
// Lombok: конструктор со всеми полями — используется @Builder под капотом
@AllArgsConstructor
// @Entity — Hibernate управляет этим классом, маппит на таблицу food_items
@Entity
// Явное имя таблицы — иначе Hibernate использует имя класса "food_item" (с underscore)
@Table(name = "food_items")
public class FoodItem {

    // Первичный ключ, генерируется PostgreSQL через SERIAL (autoincrement)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Название позиции меню: "Попкорн", "Кола", etc.
    @Column(nullable = false)
    private String name;

    // Цена в рублях. BigDecimal — обязательно для денежных значений (нет проблем округления float/double).
    // precision=10: максимум 10 цифр всего; scale=2: 2 цифры после запятой → max 99999999.99 руб
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // Категория товара: DRINK/POPCORN/SNACK/OTHER.
    // EnumType.STRING — хранится строка "DRINK", а не индекс 0 (безопасно при изменении порядка enum).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodCategory category;
}
