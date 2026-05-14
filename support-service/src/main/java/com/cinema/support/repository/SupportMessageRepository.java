package com.cinema.support.repository;

import com.cinema.support.entity.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// @Repository — Spring Data JPA репозиторий для сущности SupportMessage.
// Spring автоматически создаёт реализацию при старте через CGLIB proxy.
@Repository
// JpaRepository<SupportMessage, Long>:
//   SupportMessage — тип управляемой сущности
//   Long — тип первичного ключа
// Наследуем: save(), findById(), findAll(), deleteAll(), count() и др.
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    // findByTicketId — получить все сообщения по id тикета.
    // Spring Data генерирует: SELECT * FROM support_messages WHERE ticket_id = ?
    // Используется в: SupportService.getMessages(ticketId, userId, role)
    // Возвращает сообщения в естественном порядке вставки (id ASC).
    // Если нужна сортировка по времени, можно добавить: findByTicketIdOrderBySentAtAsc
    List<SupportMessage> findByTicketId(Long ticketId);
}
