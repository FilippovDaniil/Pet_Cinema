package com.cinema.support.repository;

import com.cinema.support.entity.SupportTicket;
import com.cinema.support.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// @Repository — маркер слоя данных. Spring создаёт proxy-реализацию во время сканирования.
// Также оборачивает JDBC исключения в Spring DataAccessException.
@Repository
// JpaRepository<SupportTicket, Long> — Spring Data JPA предоставляет реализацию "из коробки":
//   save(), findById(), findAll(), deleteAll(), count() и другие CRUD методы.
// Long — тип первичного ключа (@Id поле id в SupportTicket).
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    // findByClientId — Spring Data автоматически генерирует запрос по имени метода:
    // SELECT * FROM support_tickets WHERE client_id = ?
    // Используется в: SupportService.getMyTickets(clientId)
    // Возвращает все тикеты конкретного клиента (история обращений).
    List<SupportTicket> findByClientId(Long clientId);

    // findByAdminId — получить все тикеты, назначенные конкретному администратору.
    // SELECT * FROM support_tickets WHERE admin_id = ?
    // Не используется напрямую в текущем коде, но полезен для будущего дашборда.
    List<SupportTicket> findByAdminId(Long adminId);

    // findByStatus — получить тикеты по статусу (OPEN или CLOSED).
    // SELECT * FROM support_tickets WHERE status = ? (строка, т.к. @Enumerated(STRING))
    // Полезен для фильтрации: показать все открытые тикеты администратору.
    List<SupportTicket> findByStatus(TicketStatus status);
}
