package com.cinema.movie.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data              // Lombok: генерирует геттеры, сеттеры, equals, hashCode, toString
@Builder           // Lombok: Builder-паттерн — Genre.builder().name("Action").build()
@NoArgsConstructor // Lombok: пустой конструктор — обязателен для Hibernate (он создаёт объекты через него)
@AllArgsConstructor// Lombok: конструктор со всеми полями — нужен для @Builder
@Entity            // JPA: этот класс — таблица в БД
@Table(name = "genres") // JPA: имя таблицы "genres" (по умолчанию было бы "genre")
public class Genre {

    @Id // Это первичный ключ таблицы
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // IDENTITY — БД сама генерирует id через SERIAL/BIGSERIAL (PostgreSQL autoincrement)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    // nullable = false → колонка NOT NULL в SQL
    // unique = true    → уникальный индекс: нельзя создать два жанра с одним именем
    // length = 100     → VARCHAR(100) вместо VARCHAR(255) по умолчанию
    private String name;
}
