package com.cinema.auth.entity; // Пакет JPA-сущностей auth-service

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {
    // Хранит refresh-токены в PostgreSQL.
    // Двойная защита от злоупотребления: поле revoked в БД + ключ в Redis-blacklist.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000) // length = 1000 т.к. JWT-строка длиннее стандартных 255 символов
    private String token; // Сам JWT refresh-токен (строка)

    @ManyToOne(fetch = FetchType.LAZY) // Многие токены → один пользователь. LAZY = не загружать User пока не обратились
    @JoinColumn(name = "user_id")      // FK-колонка: user_id → users.id
    private User user; // Владелец токена

    @Column(nullable = false)
    private LocalDateTime expiryDate; // Когда токен истекает (обычно now + 7 дней)

    @Column(nullable = false)
    private boolean revoked = false;  // true = токен отозван (logout или ротация). По умолчанию false при Builder.
                                      // ВАЖНО: Lombok @Builder не использует field initializer — нужно указывать revoked=false в Builder
}
