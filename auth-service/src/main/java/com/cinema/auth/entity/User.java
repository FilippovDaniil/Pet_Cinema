package com.cinema.auth.entity; // Пакет JPA-сущностей auth-service

import jakarta.persistence.*;                        // Аннотации JPA: @Entity, @Table, @Id и т.д.
import lombok.*;                                     // Lombok: @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor
import org.hibernate.annotations.CreationTimestamp;  // Автоматически ставит время при создании записи
import org.hibernate.annotations.UpdateTimestamp;    // Автоматически обновляет время при изменении записи
import java.time.LocalDateTime;

@Entity  // JPA: класс является сущностью (таблицей в БД)
@Table(name = "users") // Имя таблицы в PostgreSQL. Без этой аннотации Hibernate использовал бы имя класса "user"
                        // ВАЖНО: "user" — зарезервированное слово в PostgreSQL, поэтому явно указываем "users"
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id // Поле является первичным ключом
    @GeneratedValue(strategy = GenerationType.IDENTITY) // БД автоматически назначает следующий id (SERIAL в PostgreSQL)
    private Long id;

    @Column(unique = true, nullable = false) // UNIQUE + NOT NULL в DDL
    private String username; // Логин пользователя — уникален в системе

    @Column(unique = true, nullable = false)
    private String email; // Email пользователя — уникален в системе

    @Column(nullable = false)
    private String password; // BCrypt-хеш пароля. Формат: $2a$10$... Никогда не хранится в открытом виде

    @Enumerated(EnumType.STRING) // Сохраняем строку "ROLE_CLIENT" вместо числа (EnumType.ORDINAL = 0, 1, 2)
                                 // EnumType.STRING устойчив к изменению порядка значений в enum
    @Column(nullable = false)
    private Role role; // Роль пользователя: ROLE_CLIENT, ROLE_SELLER или ROLE_ADMIN

    @CreationTimestamp // Hibernate автоматически заполнит при первом INSERT
    private LocalDateTime createdAt;

    @UpdateTimestamp   // Hibernate обновит при каждом UPDATE
    private LocalDateTime updatedAt;
}
