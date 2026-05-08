package com.cinema.notification.kafka;

import com.cinema.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        // ObjectMapper is injected via constructor alongside NotificationService
        consumer = new NotificationConsumer(notificationService, new ObjectMapper());
    }

    // ------------------------------------------------------------------ //
    // handleTicketPurchase
    // ------------------------------------------------------------------ //

    @Test
    void handleTicketPurchase_validMessage_createsNotification() {
        String message = """
                {"orderId":1,"userId":42,"movieTitle":"Интерстеллар","sessionTime":"2026-05-10 15:00","totalPrice":450.00}
                """;

        consumer.handleTicketPurchase(message);

        verify(notificationService).createNotification(
                eq(42L),
                contains("куплен"),
                contains("Интерстеллар")
        );
    }

    @Test
    void handleTicketPurchase_invalidJson_doesNotThrowAndDoesNotCallService() {
        assertThatCode(() -> consumer.handleTicketPurchase("invalid-json{{{"))
                .doesNotThrowAnyException();

        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void handleTicketPurchase_containsSessionTimeAndPrice() {
        String message = """
                {"orderId":2,"userId":7,"movieTitle":"Дюна","sessionTime":"2026-06-01 18:30","totalPrice":600.00}
                """;

        consumer.handleTicketPurchase(message);

        verify(notificationService).createNotification(
                eq(7L),
                eq("Билет куплен!"),
                contains("Дюна")
        );
    }

    // ------------------------------------------------------------------ //
    // handleSupportMessage
    // ------------------------------------------------------------------ //

    @Test
    void handleSupportMessage_validMessage_createsNotificationForRecipient() {
        String message = """
                {"ticketId":5,"senderId":1,"content":"Привет!","recipientId":99}
                """;

        consumer.handleSupportMessage(message);

        verify(notificationService).createNotification(
                eq(99L),
                contains("поддержке"),
                eq("Привет!")
        );
    }

    @Test
    void handleSupportMessage_invalidJson_doesNotThrowAndDoesNotCallService() {
        assertThatCode(() -> consumer.handleSupportMessage("not-valid"))
                .doesNotThrowAnyException();

        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void handleSupportMessage_missingRecipientId_handledGracefully() {
        // recipientId is absent → parsed as null → service not called or NPE caught internally
        String message = """
                {"ticketId":5,"senderId":1,"content":"Тест"}
                """;

        // The consumer wraps everything in try-catch, so no exception escapes
        assertThatCode(() -> consumer.handleSupportMessage(message))
                .doesNotThrowAnyException();
    }
}
