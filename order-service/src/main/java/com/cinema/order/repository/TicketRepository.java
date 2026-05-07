package com.cinema.order.repository;

import com.cinema.order.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByOrderId(Long orderId);

    Optional<Ticket> findBySessionIdAndSeatRowAndSeatNumber(Long sessionId, int seatRow, int seatNumber);

    List<Ticket> findByUserId(Long userId);
}
