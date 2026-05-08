package com.cinema.support.service;

import com.cinema.dto.event.SupportMessageEvent;
import com.cinema.dto.support.SupportMessageDto;
import com.cinema.dto.support.SupportMessageRequest;
import com.cinema.dto.support.SupportTicketCreateRequest;
import com.cinema.dto.support.SupportTicketDto;
import com.cinema.support.entity.SupportMessage;
import com.cinema.support.entity.SupportTicket;
import com.cinema.support.entity.TicketStatus;
import com.cinema.support.exception.AccessDeniedException;
import com.cinema.support.exception.ResourceNotFoundException;
import com.cinema.support.repository.SupportMessageRepository;
import com.cinema.support.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private SupportMessageRepository supportMessageRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private SupportService supportService;

    @Captor
    private ArgumentCaptor<SupportTicket> ticketCaptor;

    @Captor
    private ArgumentCaptor<SupportMessage> messageCaptor;

    @Captor
    private ArgumentCaptor<Object> kafkaEventCaptor;

    // ------------------------------------------------------------------ helpers

    private SupportTicket buildTicket(Long id, Long clientId, Long adminId) {
        return SupportTicket.builder()
                .id(id)
                .clientId(clientId)
                .adminId(adminId)
                .subject("Test Subject")
                .status(TicketStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private SupportMessage buildMessage(Long id, Long ticketId, Long senderId) {
        return SupportMessage.builder()
                .id(id)
                .ticketId(ticketId)
                .senderId(senderId)
                .content("Hello support")
                .sentAt(LocalDateTime.now())
                .build();
    }

    // ------------------------------------------------------------------ createTicket

    @Test
    void createTicket_success_savedWithOpenStatusAndClientId() {
        SupportTicketCreateRequest request = SupportTicketCreateRequest.builder()
                .subject("My issue").build();

        SupportTicket saved = buildTicket(1L, 42L, null);
        saved.setSubject("My issue");
        saved.setStatus(TicketStatus.OPEN);

        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(saved);

        SupportTicketDto result = supportService.createTicket(request, 42L);

        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket captured = ticketCaptor.getValue();

        assertThat(captured.getClientId()).isEqualTo(42L);
        assertThat(captured.getSubject()).isEqualTo("My issue");
        assertThat(captured.getStatus()).isEqualTo(TicketStatus.OPEN);

        assertThat(result.getClientId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void createTicket_setsTimestamps_notNull() {
        SupportTicketCreateRequest request = SupportTicketCreateRequest.builder()
                .subject("Timestamps test").build();

        SupportTicket saved = buildTicket(2L, 10L, null);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(saved);

        supportService.createTicket(request, 10L);

        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket captured = ticketCaptor.getValue();

        assertThat(captured.getCreatedAt()).isNotNull();
        assertThat(captured.getUpdatedAt()).isNotNull();
    }

    // ------------------------------------------------------------------ getMyTickets

    @Test
    void getMyTickets_returnsList() {
        SupportTicket t1 = buildTicket(1L, 5L, null);
        SupportTicket t2 = buildTicket(2L, 5L, null);

        when(supportTicketRepository.findByClientId(5L)).thenReturn(List.of(t1, t2));

        List<SupportTicketDto> result = supportService.getMyTickets(5L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getClientId()).isEqualTo(5L);
        assertThat(result.get(1).getClientId()).isEqualTo(5L);
    }

    // ------------------------------------------------------------------ getAllTickets

    @Test
    void getAllTickets_returnsAll() {
        SupportTicket t1 = buildTicket(1L, 1L, null);
        SupportTicket t2 = buildTicket(2L, 2L, null);
        SupportTicket t3 = buildTicket(3L, 3L, 10L);

        when(supportTicketRepository.findAll()).thenReturn(List.of(t1, t2, t3));

        List<SupportTicketDto> result = supportService.getAllTickets();

        assertThat(result).hasSize(3);
    }

    // ------------------------------------------------------------------ sendMessage

    @Test
    void sendMessage_byClient_ownerAccess_kafkaPublishedWithAdminRecipient() {
        SupportTicket ticket = buildTicket(1L, 1L, 2L);  // adminId=2
        SupportMessage saved = buildMessage(10L, 1L, 1L);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(saved);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportMessageRequest req = SupportMessageRequest.builder().content("Hello").build();

        SupportMessageDto result = supportService.sendMessage(1L, req, 1L, "CLIENT");

        verify(supportMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSenderId()).isEqualTo(1L);
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("Hello");

        // recipientId = adminId = 2
        verify(kafkaTemplate).send(eq("support-message"), eq("1"), kafkaEventCaptor.capture());
        SupportMessageEvent event = (SupportMessageEvent) kafkaEventCaptor.getValue();
        assertThat(event.getRecipientId()).isEqualTo(2L);
        assertThat(event.getSenderId()).isEqualTo(1L);
        assertThat(event.getTicketId()).isEqualTo(1L);
        assertThat(event.getContent()).isEqualTo("Hello");

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void sendMessage_byAdmin_anyAccess_kafkaPublishedWithClientRecipient() {
        SupportTicket ticket = buildTicket(1L, 1L, 99L);  // clientId=1, adminId=99
        SupportMessage saved = buildMessage(20L, 1L, 99L);
        saved.setContent("Admin reply");

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(saved);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportMessageRequest req = SupportMessageRequest.builder().content("Admin reply").build();

        supportService.sendMessage(1L, req, 99L, "ADMIN");

        // recipientId = clientId = 1 (isAdmin ? ticket.getClientId() : ticket.getAdminId())
        verify(kafkaTemplate).send(eq("support-message"), eq("1"), kafkaEventCaptor.capture());
        SupportMessageEvent event = (SupportMessageEvent) kafkaEventCaptor.getValue();
        assertThat(event.getRecipientId()).isEqualTo(1L);
        assertThat(event.getSenderId()).isEqualTo(99L);
    }

    @Test
    void sendMessage_byAdmin_noRecipient_kafkaNotCalled() {
        // CLIENT sends message when adminId is null → recipientId = null → kafka NOT sent
        SupportTicket ticketNoAdmin = buildTicket(2L, 1L, null);  // clientId=1, adminId=null
        SupportMessage savedMsg = buildMessage(31L, 2L, 1L);

        when(supportTicketRepository.findById(2L)).thenReturn(Optional.of(ticketNoAdmin));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(savedMsg);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticketNoAdmin);

        SupportMessageRequest req = SupportMessageRequest.builder().content("Client msg no admin").build();
        supportService.sendMessage(2L, req, 1L, "CLIENT");

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void sendMessage_notOwnerNotAdmin_throwsAccessDeniedException() {
        SupportTicket ticket = buildTicket(1L, 1L, null);  // clientId=1

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        SupportMessageRequest req = SupportMessageRequest.builder().content("Unauthorized").build();

        // senderId=5 is not owner (clientId=1) and role=CLIENT
        assertThatThrownBy(() -> supportService.sendMessage(1L, req, 5L, "CLIENT"))
                .isInstanceOf(AccessDeniedException.class);

        verify(supportMessageRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void sendMessage_ticketNotFound_throwsResourceNotFoundException() {
        when(supportTicketRepository.findById(999L)).thenReturn(Optional.empty());

        SupportMessageRequest req = SupportMessageRequest.builder().content("msg").build();

        assertThatThrownBy(() -> supportService.sendMessage(999L, req, 1L, "CLIENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ------------------------------------------------------------------ getMessages

    @Test
    void getMessages_ownerCanAccess_returnsMessages() {
        SupportTicket ticket = buildTicket(1L, 7L, null);
        SupportMessage msg = buildMessage(1L, 1L, 7L);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.findByTicketId(1L)).thenReturn(List.of(msg));

        List<SupportMessageDto> result = supportService.getMessages(1L, 7L, "CLIENT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSenderId()).isEqualTo(7L);
    }

    @Test
    void getMessages_adminCanAccess_returnsMessages() {
        SupportTicket ticket = buildTicket(1L, 7L, 20L);
        SupportMessage msg1 = buildMessage(1L, 1L, 7L);
        SupportMessage msg2 = buildMessage(2L, 1L, 20L);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.findByTicketId(1L)).thenReturn(List.of(msg1, msg2));

        // Admin (different from clientId) can still access
        List<SupportMessageDto> result = supportService.getMessages(1L, 99L, "ADMIN");

        assertThat(result).hasSize(2);
    }

    @Test
    void getMessages_strangerCannotAccess_throwsAccessDeniedException() {
        SupportTicket ticket = buildTicket(1L, 7L, null);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        // userId=55 is not owner (clientId=7) and role=CLIENT
        assertThatThrownBy(() -> supportService.getMessages(1L, 55L, "CLIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMessages_ticketNotFound_throwsResourceNotFoundException() {
        when(supportTicketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.getMessages(404L, 1L, "CLIENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    // ------------------------------------------------------------------ assignAdmin

    @Test
    void assignAdmin_success_setsAdminIdAndUpdatedAt() {
        SupportTicket ticket = buildTicket(1L, 5L, null);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportTicketDto result = supportService.assignAdmin(1L, 88L);

        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket captured = ticketCaptor.getValue();

        assertThat(captured.getAdminId()).isEqualTo(88L);
        assertThat(captured.getUpdatedAt()).isNotNull();
    }

    @Test
    void assignAdmin_notFound_throwsResourceNotFoundException() {
        when(supportTicketRepository.findById(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.assignAdmin(777L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("777");

        verify(supportTicketRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ closeTicket

    @Test
    void closeTicket_success_setsStatusClosed() {
        SupportTicket ticket = buildTicket(1L, 5L, null);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportTicketDto result = supportService.closeTicket(1L);

        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket captured = ticketCaptor.getValue();

        assertThat(captured.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(captured.getUpdatedAt()).isNotNull();
    }

    @Test
    void closeTicket_alreadyClosed_stillSetsClosed() {
        SupportTicket ticket = buildTicket(2L, 5L, null);
        ticket.setStatus(TicketStatus.CLOSED);

        when(supportTicketRepository.findById(2L)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportTicketDto result = supportService.closeTicket(2L);

        verify(supportTicketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(result.getStatus()).isEqualTo("CLOSED");
    }

    // ------------------------------------------------------------------ kafkaPublished_verifyCapturedEvent

    @Test
    void kafkaPublished_verifyCapturedEvent_containsCorrectFields() {
        SupportTicket ticket = buildTicket(5L, 10L, 20L);
        SupportMessage saved = buildMessage(100L, 5L, 20L);
        saved.setContent("Event verify");

        when(supportTicketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(saved);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportMessageRequest req = SupportMessageRequest.builder().content("Event verify").build();

        // Admin sends → recipientId = clientId = 10
        supportService.sendMessage(5L, req, 20L, "ADMIN");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), kafkaEventCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("support-message");
        assertThat(keyCaptor.getValue()).isEqualTo("5");

        SupportMessageEvent event = (SupportMessageEvent) kafkaEventCaptor.getValue();
        assertThat(event.getTicketId()).isEqualTo(5L);
        assertThat(event.getSenderId()).isEqualTo(20L);
        assertThat(event.getContent()).isEqualTo("Event verify");
        assertThat(event.getRecipientId()).isEqualTo(10L);
    }
}
