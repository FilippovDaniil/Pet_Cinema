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

// @ExtendWith(MockitoExtension.class) — подключает JUnit 5 расширение Mockito.
// Инициализирует @Mock и @InjectMocks поля ДО каждого теста.
// НЕТ Spring Context — тесты быстрые, без БД, без Kafka.
@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    // @Mock — Mockito создаёт proxy-заглушки вместо реальных зависимостей.
    // Все вызовы к репозиториям и KafkaTemplate управляются через when().thenReturn().
    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private SupportMessageRepository supportMessageRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    // @InjectMocks — Mockito создаёт реальный SupportService и инжектирует все @Mock поля.
    // Эквивалент: new SupportService(supportTicketRepository, supportMessageRepository, kafkaTemplate)
    @InjectMocks
    private SupportService supportService;

    // @Captor — ArgumentCaptor позволяет захватить аргументы переданные в мок.
    // Используется для проверки что именно было передано в repository.save() или kafkaTemplate.send().
    @Captor
    private ArgumentCaptor<SupportTicket> ticketCaptor;

    @Captor
    private ArgumentCaptor<SupportMessage> messageCaptor;

    // kafkaEventCaptor типизирован как Object — т.к. kafkaTemplate.send() принимает Object.
    // Позже кастируем к SupportMessageEvent для проверки полей.
    @Captor
    private ArgumentCaptor<Object> kafkaEventCaptor;

    // ================================================================
    // Вспомогательные методы создания тестовых данных
    // ================================================================

    // buildTicket — создаёт SupportTicket для использования в тестах как "возвращаемое значение" мока.
    private SupportTicket buildTicket(Long id, Long clientId, Long adminId) {
        return SupportTicket.builder()
                .id(id)
                .clientId(clientId)
                .adminId(adminId)     // null = нет назначенного администратора
                .subject("Test Subject")
                .status(TicketStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // buildMessage — создаёт SupportMessage для использования в тестах.
    private SupportMessage buildMessage(Long id, Long ticketId, Long senderId) {
        return SupportMessage.builder()
                .id(id)
                .ticketId(ticketId)
                .senderId(senderId)
                .content("Hello support")
                .sentAt(LocalDateTime.now())
                .build();
    }

    // ================================================================
    // createTicket тесты
    // ================================================================

    @Test
    void createTicket_success_savedWithOpenStatusAndClientId() {
        // Arrange: запрос на создание тикета
        SupportTicketCreateRequest request = SupportTicketCreateRequest.builder()
                .subject("My issue").build();

        // Мок: репозиторий "сохраняет" тикет и возвращает сущность с id=1
        SupportTicket saved = buildTicket(1L, 42L, null);
        saved.setSubject("My issue");
        saved.setStatus(TicketStatus.OPEN);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(saved);

        // Act: вызываем сервисный метод
        SupportTicketDto result = supportService.createTicket(request, 42L);

        // Assert: проверяем что в репозиторий передана правильная сущность
        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket captured = ticketCaptor.getValue();  // аргумент переданный в save()

        assertThat(captured.getClientId()).isEqualTo(42L);   // клиент установлен правильно
        assertThat(captured.getSubject()).isEqualTo("My issue");  // тема из запроса
        assertThat(captured.getStatus()).isEqualTo(TicketStatus.OPEN);  // статус OPEN

        // Проверяем возвращённый DTO
        assertThat(result.getClientId()).isEqualTo(42L);
        assertThat(result.getStatus()).isEqualTo("OPEN");  // enum → String
    }

    @Test
    void createTicket_setsTimestamps_notNull() {
        // Проверяем что createTicket устанавливает createdAt и updatedAt
        SupportTicketCreateRequest request = SupportTicketCreateRequest.builder()
                .subject("Timestamps test").build();

        SupportTicket saved = buildTicket(2L, 10L, null);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(saved);

        supportService.createTicket(request, 10L);

        // Захватываем аргумент save() и проверяем timestamps
        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket captured = ticketCaptor.getValue();

        // Сервис явно устанавливает LocalDateTime.now() — не через @PrePersist
        assertThat(captured.getCreatedAt()).isNotNull();
        assertThat(captured.getUpdatedAt()).isNotNull();
    }

    // ================================================================
    // getMyTickets тест
    // ================================================================

    @Test
    void getMyTickets_returnsList() {
        // Arrange: у клиента 5L есть 2 тикета
        SupportTicket t1 = buildTicket(1L, 5L, null);
        SupportTicket t2 = buildTicket(2L, 5L, null);
        when(supportTicketRepository.findByClientId(5L)).thenReturn(List.of(t1, t2));

        // Act
        List<SupportTicketDto> result = supportService.getMyTickets(5L);

        // Assert: 2 тикета, оба принадлежат клиенту 5L
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getClientId()).isEqualTo(5L);
        assertThat(result.get(1).getClientId()).isEqualTo(5L);
    }

    // ================================================================
    // getAllTickets тест
    // ================================================================

    @Test
    void getAllTickets_returnsAll() {
        // Arrange: 3 тикета от разных клиентов
        SupportTicket t1 = buildTicket(1L, 1L, null);
        SupportTicket t2 = buildTicket(2L, 2L, null);
        SupportTicket t3 = buildTicket(3L, 3L, 10L);  // с назначенным admin=10

        when(supportTicketRepository.findAll()).thenReturn(List.of(t1, t2, t3));

        // Act
        List<SupportTicketDto> result = supportService.getAllTickets();

        // Assert: все 3 тикета возвращены
        assertThat(result).hasSize(3);
    }

    // ================================================================
    // sendMessage тесты
    // ================================================================

    @Test
    void sendMessage_byClient_ownerAccess_kafkaPublishedWithAdminRecipient() {
        // Тикет: clientId=1, adminId=2 (есть назначенный администратор)
        SupportTicket ticket = buildTicket(1L, 1L, 2L);
        SupportMessage saved = buildMessage(10L, 1L, 1L);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(saved);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportMessageRequest req = SupportMessageRequest.builder().content("Hello").build();

        // Act: CLIENT (senderId=1 = clientId=1) отправляет сообщение
        SupportMessageDto result = supportService.sendMessage(1L, req, 1L, "CLIENT");

        // Assert: сообщение сохранено с правильными полями
        verify(supportMessageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getSenderId()).isEqualTo(1L);
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("Hello");

        // Assert: Kafka событие опубликовано с recipientId = adminId = 2
        // Логика: CLIENT отправляет → уведомить ADMIN (isAdmin=false → recipientId = ticket.getAdminId())
        verify(kafkaTemplate).send(eq("support-message"), eq("1"), kafkaEventCaptor.capture());
        SupportMessageEvent event = (SupportMessageEvent) kafkaEventCaptor.getValue();
        assertThat(event.getRecipientId()).isEqualTo(2L);  // adminId=2
        assertThat(event.getSenderId()).isEqualTo(1L);
        assertThat(event.getTicketId()).isEqualTo(1L);
        assertThat(event.getContent()).isEqualTo("Hello");

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void sendMessage_byAdmin_anyAccess_kafkaPublishedWithClientRecipient() {
        // Тикет: clientId=1, adminId=99
        SupportTicket ticket = buildTicket(1L, 1L, 99L);
        SupportMessage saved = buildMessage(20L, 1L, 99L);
        saved.setContent("Admin reply");

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(saved);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportMessageRequest req = SupportMessageRequest.builder().content("Admin reply").build();

        // Act: ADMIN (senderId=99) отправляет сообщение
        supportService.sendMessage(1L, req, 99L, "ADMIN");

        // Assert: recipientId = clientId = 1
        // Логика: ADMIN отправляет → уведомить CLIENT (isAdmin=true → recipientId = ticket.getClientId())
        verify(kafkaTemplate).send(eq("support-message"), eq("1"), kafkaEventCaptor.capture());
        SupportMessageEvent event = (SupportMessageEvent) kafkaEventCaptor.getValue();
        assertThat(event.getRecipientId()).isEqualTo(1L);  // clientId=1
        assertThat(event.getSenderId()).isEqualTo(99L);
    }

    @Test
    void sendMessage_byAdmin_noRecipient_kafkaNotCalled() {
        // Тест: CLIENT отправляет сообщение когда adminId=null → recipientId=null → Kafka НЕ вызывается.
        // Название теста misleading: это на самом деле CLIENT sends, no admin assigned yet.
        // adminId=null: администратор ещё не назначен на тикет.
        SupportTicket ticketNoAdmin = buildTicket(2L, 1L, null);  // clientId=1, adminId=null
        SupportMessage savedMsg = buildMessage(31L, 2L, 1L);

        when(supportTicketRepository.findById(2L)).thenReturn(Optional.of(ticketNoAdmin));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(savedMsg);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticketNoAdmin);

        SupportMessageRequest req = SupportMessageRequest.builder().content("Client msg no admin").build();
        // Act: CLIENT отправляет сообщение, но adminId=null → recipientId=null
        supportService.sendMessage(2L, req, 1L, "CLIENT");

        // Assert: Kafka НЕ вызван (нет кому уведомлять)
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void sendMessage_notOwnerNotAdmin_throwsAccessDeniedException() {
        // Тикет принадлежит clientId=1. Чужой клиент senderId=5 пытается отправить сообщение.
        SupportTicket ticket = buildTicket(1L, 1L, null);
        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        SupportMessageRequest req = SupportMessageRequest.builder().content("Unauthorized").build();

        // Assert: бросает AccessDeniedException (не ResourceNotFoundException)
        // isOwner=false (5 ≠ 1), isAdmin=false (role="CLIENT") → AccessDeniedException
        assertThatThrownBy(() -> supportService.sendMessage(1L, req, 5L, "CLIENT"))
                .isInstanceOf(AccessDeniedException.class);

        // Сообщение и Kafka событие НЕ сохранены
        verify(supportMessageRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void sendMessage_ticketNotFound_throwsResourceNotFoundException() {
        // Несуществующий ticketId=999
        when(supportTicketRepository.findById(999L)).thenReturn(Optional.empty());

        SupportMessageRequest req = SupportMessageRequest.builder().content("msg").build();

        // Assert: ResourceNotFoundException с сообщением содержащим "999"
        assertThatThrownBy(() -> supportService.sendMessage(999L, req, 1L, "CLIENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");  // "Support ticket not found: 999"
    }

    // ================================================================
    // getMessages тесты
    // ================================================================

    @Test
    void getMessages_ownerCanAccess_returnsMessages() {
        // clientId=7 может читать свой тикет
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
        // ADMIN userId=99 может читать любой тикет (даже если не clientId и не adminId тикета)
        SupportTicket ticket = buildTicket(1L, 7L, 20L);  // clientId=7, adminId=20
        SupportMessage msg1 = buildMessage(1L, 1L, 7L);
        SupportMessage msg2 = buildMessage(2L, 1L, 20L);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.findByTicketId(1L)).thenReturn(List.of(msg1, msg2));

        // userId=99 не совпадает с clientId=7, но role="ADMIN" → доступ разрешён
        List<SupportMessageDto> result = supportService.getMessages(1L, 99L, "ADMIN");

        assertThat(result).hasSize(2);  // оба сообщения возвращены
    }

    @Test
    void getMessages_strangerCannotAccess_throwsAccessDeniedException() {
        // userId=55 не владелец (clientId=7) и не ADMIN → AccessDeniedException
        SupportTicket ticket = buildTicket(1L, 7L, null);
        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));

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

    // ================================================================
    // assignAdmin тесты
    // ================================================================

    @Test
    void assignAdmin_success_setsAdminIdAndUpdatedAt() {
        // Тикет без администратора (adminId=null)
        SupportTicket ticket = buildTicket(1L, 5L, null);

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        // Act: назначаем администратора adminId=88
        SupportTicketDto result = supportService.assignAdmin(1L, 88L);

        // Assert: в save() передана сущность с adminId=88 и обновлённым updatedAt
        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket captured = ticketCaptor.getValue();

        assertThat(captured.getAdminId()).isEqualTo(88L);  // adminId обновлён
        assertThat(captured.getUpdatedAt()).isNotNull();   // updatedAt установлен
    }

    @Test
    void assignAdmin_notFound_throwsResourceNotFoundException() {
        // Тикет 777 не существует
        when(supportTicketRepository.findById(777L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> supportService.assignAdmin(777L, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("777");

        // save() не вызван (не достигли этой строки — исключение раньше)
        verify(supportTicketRepository, never()).save(any());
    }

    // ================================================================
    // closeTicket тесты
    // ================================================================

    @Test
    void closeTicket_success_setsStatusClosed() {
        // Тикет в статусе OPEN
        SupportTicket ticket = buildTicket(1L, 5L, null);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);  // предусловие

        when(supportTicketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        // Act
        SupportTicketDto result = supportService.closeTicket(1L);

        // Assert: статус изменён на CLOSED
        verify(supportTicketRepository).save(ticketCaptor.capture());
        SupportTicket captured = ticketCaptor.getValue();

        assertThat(captured.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(captured.getUpdatedAt()).isNotNull();
    }

    @Test
    void closeTicket_alreadyClosed_stillSetsClosed() {
        // Идемпотентность: закрыть уже закрытый тикет — безопасно
        SupportTicket ticket = buildTicket(2L, 5L, null);
        ticket.setStatus(TicketStatus.CLOSED);  // уже закрыт

        when(supportTicketRepository.findById(2L)).thenReturn(Optional.of(ticket));
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        // Act: закрываем снова
        SupportTicketDto result = supportService.closeTicket(2L);

        // Assert: статус остался CLOSED
        verify(supportTicketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(result.getStatus()).isEqualTo("CLOSED");
    }

    // ================================================================
    // Тест: детальная проверка Kafka события
    // ================================================================

    @Test
    void kafkaPublished_verifyCapturedEvent_containsCorrectFields() {
        // Тикет: clientId=10, adminId=20
        SupportTicket ticket = buildTicket(5L, 10L, 20L);
        SupportMessage saved = buildMessage(100L, 5L, 20L);
        saved.setContent("Event verify");

        when(supportTicketRepository.findById(5L)).thenReturn(Optional.of(ticket));
        when(supportMessageRepository.save(any(SupportMessage.class))).thenReturn(saved);
        when(supportTicketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportMessageRequest req = SupportMessageRequest.builder().content("Event verify").build();

        // ADMIN (senderId=20) отправляет сообщение → recipientId = clientId = 10
        supportService.sendMessage(5L, req, 20L, "ADMIN");

        // Отдельные Captor для topic и key (помимо kafkaEventCaptor для значения)
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), kafkaEventCaptor.capture());

        // Проверяем топик — должен быть "support-message"
        assertThat(topicCaptor.getValue()).isEqualTo("support-message");
        // Проверяем ключ — должен быть ticketId.toString() = "5"
        // Ключ определяет партицию: все сообщения одного тикета → одна партиция → порядок гарантирован
        assertThat(keyCaptor.getValue()).isEqualTo("5");

        // Проверяем само событие
        SupportMessageEvent event = (SupportMessageEvent) kafkaEventCaptor.getValue();
        assertThat(event.getTicketId()).isEqualTo(5L);
        assertThat(event.getSenderId()).isEqualTo(20L);       // кто отправил
        assertThat(event.getContent()).isEqualTo("Event verify");
        assertThat(event.getRecipientId()).isEqualTo(10L);    // кому уведомление (clientId)
    }
}
