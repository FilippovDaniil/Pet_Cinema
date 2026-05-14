package com.cinema.hall.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime; // Дата+время без timezone — достаточно для расписания кинотеатра

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sessions") // Таблица киносеансов
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // movieId — скалярное поле (Long), НЕ @ManyToOne на Movie.
    // Причина: Movie находится в movie-service (другая БД, movie_db).
    // Микросервисный принцип: сервисы НЕ имеют прямых FK между разными БД.
    // hall-service хранит только идентификатор фильма; реальные данные фильма
    // запрашиваются у movie-service через HTTP, если нужны (в данном проекте не запрашиваются).
    @Column(name = "movie_id", nullable = false)
    private Long movieId; // ID фильма из movie-service

    // @ManyToOne — много сеансов проводятся в одном зале.
    // Hall в той же БД (hall_db) → можно использовать полноценный @ManyToOne со ссылкой.
    // fetch = FetchType.LAZY — Hall не загружается сразу при SELECT сеанса.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall; // Зал, в котором проводится сеанс

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime; // Время начала сеанса

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime; // Время окончания (start + длительность фильма)

    // Базовая цена билета за это место. Итоговая цена может увеличиться на стоимость
    // выбранных доп.услуг (ExtraService) при оформлении заказа.
    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice; // Базовая цена билета в рублях

    // @Builder.Default — без этой аннотации Lombok @Builder игнорирует инициализатор поля
    // и устанавливает false (примитивный boolean по умолчанию).
    // С @Builder.Default: Session.builder().build() → active = true.
    // Мягкое удаление (soft delete): вместо DELETE из БД ставим active = false.
    // Это позволяет сохранить историю и не сломать уже купленные билеты.
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true; // true — сеанс активен и продаётся; false — отменён/скрыт
}
