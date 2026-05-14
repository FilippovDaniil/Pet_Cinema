package com.cinema.order.repository;

import com.cinema.order.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Derived query: "SELECT * FROM tickets WHERE order_id = ?".
    // Используется для получения списка билетов по id заказа (например, при отображении деталей заказа).
    List<Ticket> findByOrderId(Long orderId);

    // Ключевой метод для проверки конфликта мест!
    // "SELECT * FROM tickets WHERE session_id = ? AND seat_row = ? AND seat_number = ?"
    // Вызывается в OrderService.createTicketOrder() ПЕРЕД бронированием:
    //   если Optional.isPresent() — место уже занято → выбрасываем исключение (нельзя продать дважды).
    // Optional<Ticket> — возвращает пустой Optional если место свободно, иначе существующий Ticket.
    Optional<Ticket> findBySessionIdAndSeatRowAndSeatNumber(Long sessionId, int seatRow, int seatNumber);

    // Derived query: "SELECT * FROM tickets WHERE user_id = ?".
    // Используется для получения всех билетов пользователя (личный кабинет — история покупок).
    List<Ticket> findByUserId(Long userId);
}
