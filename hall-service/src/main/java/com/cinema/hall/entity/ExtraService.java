package com.cinema.hall.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; // Точная десятичная арифметика — обязательна для денег (float/double теряют точность)

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "extra_services") // Таблица дополнительных услуг залов
public class ExtraService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @ManyToOne — много доп.услуг принадлежат одному залу.
    // fetch = FetchType.LAZY — Hall загружается из БД только при первом обращении к полю hall.
    // Это важно: без LAZY каждый SELECT extra_service автоматически делал бы JOIN с halls.
    // Можно использовать LAZY потому что ExtraService и Hall в ОДНОЙ БД (hall_db) —
    // в отличие от, например, Review.userId, который ссылается на auth-service (другая БД).
    @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn — создаёт FK-колонку "hall_id" в таблице extra_services.
    // nullable = false — каждая услуга обязательно привязана к залу.
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall; // Зал, к которому относится эта услуга

    @Column(nullable = false, length = 255)
    private String name; // Название услуги: "Вибрация кресла", "3D-очки премиум" и т.д.

    // BigDecimal — единственно правильный тип для денежных сумм в Java.
    // float/double: 0.1 + 0.2 = 0.30000000000000004 (бинарное представление с потерями).
    // BigDecimal: 0.1 + 0.2 = 0.3 (точное десятичное представление).
    //
    // precision = 10, scale = 2 → максимум 10 цифр, из них 2 после запятой.
    // Пример: 99999999.99 — максимальная цена. Достаточно для услуг кинотеатра.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price; // Цена услуги в рублях
}
