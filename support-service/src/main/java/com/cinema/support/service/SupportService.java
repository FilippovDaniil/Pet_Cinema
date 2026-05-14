package com.cinema.support.service;

import com.cinema.dto.event.SupportMessageEvent;
import com.cinema.dto.support.*;
import com.cinema.support.entity.SupportMessage;
import com.cinema.support.entity.SupportTicket;
import com.cinema.support.entity.TicketStatus;
import com.cinema.support.exception.AccessDeniedException;
import com.cinema.support.exception.ResourceNotFoundException;
import com.cinema.support.repository.SupportMessageRepository;
import com.cinema.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

// @Slf4j — Lombok: Logger для info/warn логирования операций (создание тикета, назначение, закрытие).
@Slf4j
// @Service — маркер слоя бизнес-логики. Аналог @Component, но семантически правильнее.
@Service
// @RequiredArgsConstructor — Lombok: конструктор для всех final полей.
// Spring инжектирует: supportTicketRepository, supportMessageRepository, kafkaTemplate.
@RequiredArgsConstructor
public class SupportService {

    // Репозитории для доступа к таблицам support_tickets и support_messages.
    private final SupportTicketRepository supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;

    // KafkaTemplate<String, Object> — универсальный producer для Kafka.
    //   String — тип ключа (ticketId.toString())
    //   Object — тип значения (SupportMessageEvent сериализуется в JSON через JsonSerializer)
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ================================================================
    // createTicket — создать новый тикет поддержки
    // ================================================================

    // @Transactional — операция выполняется в одной транзакции PostgreSQL.
    // Если save() бросит исключение — транзакция откатится (данные не сохранятся).
    @Transactional
    public SupportTicketDto createTicket(SupportTicketCreateRequest req, Long clientId) {
        LocalDateTime now = LocalDateTime.now();

        // Создаём SupportTicket через Builder.
        // adminId НЕ задаётся — null по умолчанию (Lombok Builder).
        // Администратор назначается позже через assignAdmin().
        SupportTicket ticket = SupportTicket.builder()
                .clientId(clientId)              // id клиента из JWT токена
                .subject(req.getSubject())       // тема обращения из запроса
                .status(TicketStatus.OPEN)       // новый тикет всегда OPEN
                .createdAt(now)                  // явно устанавливаем время
                .updatedAt(now)
                .build();

        // Сохраняем в PostgreSQL. После save() ticket.getId() содержит реальный id из БД.
        SupportTicket saved = supportTicketRepository.save(ticket);
        log.info("Created support ticket #{} for client {}", saved.getId(), clientId);

        // Преобразуем сущность в DTO и возвращаем клиенту.
        return toTicketDto(saved);
    }

    // ================================================================
    // getMyTickets — получить все тикеты клиента
    // ================================================================

    // readOnly = true — оптимизация: Hibernate не отслеживает изменения сущностей (read-only транзакция).
    // SELECT только — нет overhead для dirty checking.
    @Transactional(readOnly = true)
    public List<SupportTicketDto> getMyTickets(Long clientId) {
        // findByClientId → SELECT * FROM support_tickets WHERE client_id = ?
        // stream().map(toTicketDto)  — конвертируем каждую сущность в DTO
        // collect(toList()) — собираем в List
        return supportTicketRepository.findByClientId(clientId).stream()
                .map(this::toTicketDto)
                .collect(Collectors.toList());
    }

    // ================================================================
    // getAllTickets — получить все тикеты (для администратора)
    // ================================================================

    @Transactional(readOnly = true)
    public List<SupportTicketDto> getAllTickets() {
        // findAll() — SELECT * FROM support_tickets (все записи, без фильтра)
        // Вызывается только ADMIN (ограничение в SupportController через @PreAuthorize).
        return supportTicketRepository.findAll().stream()
                .map(this::toTicketDto)
                .collect(Collectors.toList());
    }

    // ================================================================
    // sendMessage — отправить сообщение в тикет
    // ================================================================

    @Transactional
    public SupportMessageDto sendMessage(Long ticketId, SupportMessageRequest req, Long senderId, String role) {
        // Находим тикет по id. Если не найден — бросаем ResourceNotFoundException → HTTP 404.
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        // ---- Проверка доступа (бизнес-логика авторизации) ----
        // isAdmin: роль "ADMIN" (из JWT claim "roles").
        // ВАЖНО: сравниваем с "ADMIN", не "ROLE_ADMIN" —
        //   в этом сервисе используется "ADMIN" (без префикса ROLE_).
        boolean isAdmin = "ADMIN".equals(role);
        // isOwner: отправитель — владелец тикета (clientId совпадает).
        boolean isOwner = ticket.getClientId().equals(senderId);

        // Правило доступа:
        //   - ADMIN может писать в любой тикет (isAdmin = true)
        //   - CLIENT может писать только в свой тикет (isOwner = true)
        //   - Чужой CLIENT (isOwner=false, isAdmin=false) — AccessDeniedException → HTTP 403
        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You do not have access to this ticket");
        }

        LocalDateTime now = LocalDateTime.now();

        // Создаём сообщение.
        // ticketId — скалярный FK (управляет INSERT колонки ticket_id).
        // ticket   — навигационное свойство, не задаём (insertable=false).
        SupportMessage message = SupportMessage.builder()
                .ticketId(ticketId)              // FK: к тикету
                .senderId(senderId)              // кто отправил
                .content(req.getContent())       // текст сообщения
                .sentAt(now)                     // время отправки
                .build();

        // Сохраняем сообщение в PostgreSQL.
        SupportMessage saved = supportMessageRepository.save(message);

        // ---- Kafka уведомление ----
        // Определяем получателя уведомления:
        //   isAdmin = true  → сообщение от администратора → уведомить clientId (владельца тикета)
        //   isAdmin = false → сообщение от клиента        → уведомить adminId (назначенного адм.)
        Long recipientId = isAdmin ? ticket.getClientId() : ticket.getAdminId();

        // Публикуем событие ТОЛЬКО если получатель известен.
        // Если adminId = null (администратор ещё не назначен) → recipientId = null → не публикуем.
        // Это предотвращает уведомление "никому" и лишние Kafka сообщения.
        if (recipientId != null) {
            SupportMessageEvent event = SupportMessageEvent.builder()
                    .ticketId(ticketId)              // id тикета
                    .senderId(senderId)              // кто отправил
                    .content(req.getContent())       // текст для уведомления
                    .recipientId(recipientId)        // кому уведомить
                    .build();
            // Публикуем в топик "support-message".
            // Ключ = String.valueOf(ticketId) — все сообщения одного тикета идут в одну партицию.
            // Это гарантирует порядок обработки событий для конкретного тикета.
            kafkaTemplate.send("support-message", String.valueOf(ticketId), event);
            log.info("Published support-message event for ticket {}, recipient {}", ticketId, recipientId);
        }

        // Обновляем updatedAt тикета — чтобы фронтенд видел активность.
        // setUpdatedAt + save() — это дополнительная явная запись поверх @PreUpdate.
        ticket.setUpdatedAt(now);
        supportTicketRepository.save(ticket);

        return toMessageDto(saved);
    }

    // ================================================================
    // getMessages — получить все сообщения тикета
    // ================================================================

    @Transactional(readOnly = true)
    public List<SupportMessageDto> getMessages(Long ticketId, Long userId, String role) {
        // Находим тикет. Если не найден — ResourceNotFoundException → 404.
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        // Та же логика доступа что и в sendMessage.
        // ADMIN читает любой тикет, CLIENT — только свой.
        boolean isAdmin = "ADMIN".equals(role);
        boolean isOwner = ticket.getClientId().equals(userId);

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You do not have access to this ticket");
        }

        // findByTicketId → SELECT * FROM support_messages WHERE ticket_id = ?
        // Возвращаем список DTO (не сущностей — клиент не должен видеть internals).
        return supportMessageRepository.findByTicketId(ticketId).stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    // ================================================================
    // assignAdmin — назначить администратора на тикет
    // ================================================================

    // Вызывается только ADMIN (проверка в SupportController через @PreAuthorize).
    @Transactional
    public SupportTicketDto assignAdmin(Long ticketId, Long adminId) {
        // Находим тикет. Если не найден — ResourceNotFoundException → 404.
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        // Назначаем администратора: устанавливаем adminId.
        // До этого adminId мог быть null (новый тикет без администратора).
        ticket.setAdminId(adminId);
        ticket.setUpdatedAt(LocalDateTime.now());  // фиксируем время назначения
        SupportTicket saved = supportTicketRepository.save(ticket);

        log.info("Admin {} assigned to support ticket #{}", adminId, ticketId);
        return toTicketDto(saved);
    }

    // ================================================================
    // closeTicket — закрыть тикет поддержки
    // ================================================================

    // Вызывается только ADMIN (проверка в SupportController через @PreAuthorize).
    @Transactional
    public SupportTicketDto closeTicket(Long ticketId) {
        // Находим тикет. Если не найден — ResourceNotFoundException → 404.
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        // Меняем статус OPEN → CLOSED.
        // Если тикет уже CLOSED — это идемпотентная операция (setStatus(CLOSED) дважды безопасно).
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setUpdatedAt(LocalDateTime.now());  // фиксируем время закрытия
        SupportTicket saved = supportTicketRepository.save(ticket);

        log.info("Support ticket #{} closed", ticketId);
        return toTicketDto(saved);
    }

    // ================================================================
    // Вспомогательные методы маппинга сущность → DTO
    // ================================================================

    // toTicketDto — конвертирует SupportTicket (JPA сущность) в SupportTicketDto (ответ API).
    // Изолирует внутреннюю структуру сущности от публичного API.
    // ticket.getStatus().name() — конвертирует enum в строку: TicketStatus.OPEN → "OPEN".
    private SupportTicketDto toTicketDto(SupportTicket ticket) {
        return SupportTicketDto.builder()
                .id(ticket.getId())
                .clientId(ticket.getClientId())
                .adminId(ticket.getAdminId())    // может быть null (не назначен)
                .subject(ticket.getSubject())
                .status(ticket.getStatus().name())   // enum → String
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    // toMessageDto — конвертирует SupportMessage в SupportMessageDto.
    // Возвращает ticketId как скалярное поле (не объект тикета) — предотвращает LazyLoad.
    private SupportMessageDto toMessageDto(SupportMessage message) {
        return SupportMessageDto.builder()
                .id(message.getId())
                .ticketId(message.getTicketId())     // скалярный Long, не getTicket().getId()
                .senderId(message.getSenderId())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .build();
    }
}
