package com.cinema.support;

import com.cinema.dto.support.SupportMessageDto;
import com.cinema.dto.support.SupportMessageRequest;
import com.cinema.dto.support.SupportTicketCreateRequest;
import com.cinema.dto.support.SupportTicketDto;
import com.cinema.support.exception.AccessDeniedException;
import com.cinema.support.exception.ResourceNotFoundException;
import com.cinema.support.service.SupportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@ActiveProfiles("test")
@Testcontainers
class SupportServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("support_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @MockBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    SupportService supportService;

    @Autowired
    com.cinema.support.repository.SupportTicketRepository ticketRepository;

    @Autowired
    com.cinema.support.repository.SupportMessageRepository messageRepository;

    @BeforeEach
    void cleanup() {
        messageRepository.deleteAll();
        ticketRepository.deleteAll();
    }

    @Test
    void createTicket_persistedAndRetrievable() {
        SupportTicketDto dto = supportService.createTicket(
                new SupportTicketCreateRequest("Проблема с заказом"), 1L);

        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getClientId()).isEqualTo(1L);
        assertThat(dto.getSubject()).isEqualTo("Проблема с заказом");
        assertThat(dto.getStatus()).isEqualTo("OPEN");
        assertThat(dto.getCreatedAt()).isNotNull();
    }

    @Test
    void getMyTickets_returnsOnlyClientTickets() {
        supportService.createTicket(new SupportTicketCreateRequest("Тикет 1"), 1L);
        supportService.createTicket(new SupportTicketCreateRequest("Тикет 2"), 1L);
        supportService.createTicket(new SupportTicketCreateRequest("Чужой тикет"), 2L);

        List<SupportTicketDto> myTickets = supportService.getMyTickets(1L);

        assertThat(myTickets).hasSize(2);
        assertThat(myTickets).allMatch(t -> t.getClientId().equals(1L));
    }

    @Test
    void getAllTickets_returnsAll() {
        supportService.createTicket(new SupportTicketCreateRequest("T1"), 1L);
        supportService.createTicket(new SupportTicketCreateRequest("T2"), 2L);

        List<SupportTicketDto> all = supportService.getAllTickets();

        assertThat(all).hasSize(2);
    }

    @Test
    void sendMessage_andGetMessages_flow() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Вопрос"), 1L);

        supportService.sendMessage(ticket.getId(),
                new SupportMessageRequest("Привет, мне нужна помощь!"), 1L, "CLIENT");
        supportService.sendMessage(ticket.getId(),
                new SupportMessageRequest("Конечно, чем помочь?"), 99L, "ADMIN");

        // First assign admin so second message knows the recipient
        supportService.assignAdmin(ticket.getId(), 99L);

        List<SupportMessageDto> messages = supportService.getMessages(ticket.getId(), 1L, "CLIENT");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("Привет, мне нужна помощь!");
        assertThat(messages.get(1).getContent()).isEqualTo("Конечно, чем помочь?");
    }

    @Test
    void sendMessage_unauthorizedUser_throwsAccessDenied() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Тикет клиента 1"), 1L);

        assertThatThrownBy(() ->
                supportService.sendMessage(ticket.getId(),
                        new SupportMessageRequest("Чужое сообщение"), 2L, "CLIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getMessages_unauthorizedUser_throwsAccessDenied() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Мой тикет"), 1L);

        assertThatThrownBy(() ->
                supportService.getMessages(ticket.getId(), 2L, "CLIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignAdmin_updatesTicket() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Нужна помощь"), 1L);

        assertThat(ticket.getAdminId()).isNull();

        SupportTicketDto updated = supportService.assignAdmin(ticket.getId(), 42L);

        assertThat(updated.getAdminId()).isEqualTo(42L);
        assertThat(updated.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void closeTicket_changesStatus() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Закрыть меня"), 1L);

        SupportTicketDto closed = supportService.closeTicket(ticket.getId());

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void assignAndClose_fullFlow() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Полный флоу"), 1L);

        supportService.assignAdmin(ticket.getId(), 10L);
        SupportTicketDto closed = supportService.closeTicket(ticket.getId());

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getAdminId()).isEqualTo(10L);
    }

    @Test
    void sendMessage_ticketNotFound_throwsResourceNotFound() {
        assertThatThrownBy(() ->
                supportService.sendMessage(999L,
                        new SupportMessageRequest("Сообщение"), 1L, "CLIENT"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void closeTicket_notFound_throwsResourceNotFound() {
        assertThatThrownBy(() -> supportService.closeTicket(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminCanReadAnyTicketMessages() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Тикет"), 1L);
        supportService.sendMessage(ticket.getId(),
                new SupportMessageRequest("Вопрос"), 1L, "CLIENT");

        List<SupportMessageDto> messages = supportService.getMessages(
                ticket.getId(), 99L, "ADMIN");

        assertThat(messages).hasSize(1);
    }
}
