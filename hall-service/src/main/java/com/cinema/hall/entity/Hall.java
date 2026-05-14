package com.cinema.hall.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Lombok: генерирует геттеры, сеттеры, equals, hashCode, toString
@Data
// Lombok: генерирует паттерн Builder — Hall.builder().name("...").type(HallType.VIP).build()
@Builder
// Lombok: генерирует конструктор без аргументов — требуется JPA для создания объектов через Reflection
@NoArgsConstructor
// Lombok: генерирует конструктор со всеми полями — нужен для @Builder (иначе builder не работает)
@AllArgsConstructor
// @Entity — JPA превращает этот класс в таблицу БД. Каждый экземпляр = одна строка таблицы.
@Entity
// @Table(name = "halls") — явно задаём имя таблицы в PostgreSQL (иначе Hibernate использует имя класса)
@Table(name = "halls")
public class Hall {

    // @Id — первичный ключ таблицы
    @Id
    // @GeneratedValue(IDENTITY) — БД сама назначает id при INSERT (PostgreSQL SERIAL / BIGSERIAL).
    // Значение возвращается обратно в объект после сохранения.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // nullable = false → NOT NULL в DDL (Hibernate добавляет ограничение при create/validate)
    // length = 255 → VARCHAR(255) вместо дефолтного VARCHAR(255); явно задаём для документирования
    @Column(nullable = false, length = 255)
    private String name; // Название зала: "Зал 1", "Зал VIP" и т.д.

    // @Enumerated(EnumType.STRING) — хранит строковое имя значения ("NORMAL", "VIP" и т.д.).
    // Альтернатива ORDINAL (0, 1, 2) опасна: если добавить значение между существующими —
    // все числовые значения сдвинутся и БД станет некорректной.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HallType type; // Тип зала (NORMAL/VIP/THREE_D/FIVE_D)

    // @Column(name = ...) — явно указываем имя колонки в snake_case.
    // Без этого Hibernate по умолчанию в Spring Boot 3 создаёт колонку "rows_count" (физическое имя),
    // но явное указание делает намерение читаемым и защищает от изменений naming strategy.
    @Column(name = "rows_count", nullable = false)
    private int rowsCount; // Количество рядов в зале

    @Column(name = "seats_per_row", nullable = false)
    private int seatsPerRow; // Количество мест в каждом ряду

    // columnDefinition = "TEXT" — PostgreSQL тип TEXT (неограниченная длина).
    // Без этого Hibernate использует VARCHAR(255), что может быть мало для описания зала.
    // description может быть null (нет nullable = false) — описание опциональное.
    @Column(columnDefinition = "TEXT")
    private String description; // Описание зала (опциональное)
}
