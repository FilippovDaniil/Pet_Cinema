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

@Slf4j
@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public SupportTicketDto createTicket(SupportTicketCreateRequest req, Long clientId) {
        LocalDateTime now = LocalDateTime.now();
        SupportTicket ticket = SupportTicket.builder()
                .clientId(clientId)
                .subject(req.getSubject())
                .status(TicketStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        SupportTicket saved = supportTicketRepository.save(ticket);
        log.info("Created support ticket #{} for client {}", saved.getId(), clientId);
        return toTicketDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SupportTicketDto> getMyTickets(Long clientId) {
        return supportTicketRepository.findByClientId(clientId).stream()
                .map(this::toTicketDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SupportTicketDto> getAllTickets() {
        return supportTicketRepository.findAll().stream()
                .map(this::toTicketDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupportMessageDto sendMessage(Long ticketId, SupportMessageRequest req, Long senderId, String role) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        // Access check: client can only message their own ticket; admin can message any
        boolean isAdmin = "ADMIN".equals(role);
        boolean isOwner = ticket.getClientId().equals(senderId);

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You do not have access to this ticket");
        }

        LocalDateTime now = LocalDateTime.now();
        SupportMessage message = SupportMessage.builder()
                .ticketId(ticketId)
                .senderId(senderId)
                .content(req.getContent())
                .sentAt(now)
                .build();

        SupportMessage saved = supportMessageRepository.save(message);

        // Determine recipient for notification
        Long recipientId = isAdmin ? ticket.getClientId() : ticket.getAdminId();

        // Publish event to Kafka
        if (recipientId != null) {
            SupportMessageEvent event = SupportMessageEvent.builder()
                    .ticketId(ticketId)
                    .senderId(senderId)
                    .content(req.getContent())
                    .recipientId(recipientId)
                    .build();
            kafkaTemplate.send("support-message", String.valueOf(ticketId), event);
            log.info("Published support-message event for ticket {}, recipient {}", ticketId, recipientId);
        }

        // Update ticket updatedAt
        ticket.setUpdatedAt(now);
        supportTicketRepository.save(ticket);

        return toMessageDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SupportMessageDto> getMessages(Long ticketId, Long userId, String role) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        boolean isAdmin = "ADMIN".equals(role);
        boolean isOwner = ticket.getClientId().equals(userId);

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You do not have access to this ticket");
        }

        return supportMessageRepository.findByTicketId(ticketId).stream()
                .map(this::toMessageDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SupportTicketDto assignAdmin(Long ticketId, Long adminId) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        ticket.setAdminId(adminId);
        ticket.setUpdatedAt(LocalDateTime.now());
        SupportTicket saved = supportTicketRepository.save(ticket);

        log.info("Admin {} assigned to support ticket #{}", adminId, ticketId);
        return toTicketDto(saved);
    }

    @Transactional
    public SupportTicketDto closeTicket(Long ticketId) {
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Support ticket not found: " + ticketId));

        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setUpdatedAt(LocalDateTime.now());
        SupportTicket saved = supportTicketRepository.save(ticket);

        log.info("Support ticket #{} closed", ticketId);
        return toTicketDto(saved);
    }

    private SupportTicketDto toTicketDto(SupportTicket ticket) {
        return SupportTicketDto.builder()
                .id(ticket.getId())
                .clientId(ticket.getClientId())
                .adminId(ticket.getAdminId())
                .subject(ticket.getSubject())
                .status(ticket.getStatus().name())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    private SupportMessageDto toMessageDto(SupportMessage message) {
        return SupportMessageDto.builder()
                .id(message.getId())
                .ticketId(message.getTicketId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .build();
    }
}
