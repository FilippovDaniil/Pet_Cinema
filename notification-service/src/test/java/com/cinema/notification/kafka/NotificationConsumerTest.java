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

// @ExtendWith(MockitoExtension.class) — только Mockito, нет Spring Context.
// Быстрые юнит-тесты для Kafka consumer.
@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    // @Mock — заглушка NotificationService.
    // Перехватывает вызовы createNotification() без реальной БД.
    @Mock
    private NotificationService notificationService;

    // consumer — реальный NotificationConsumer (не мок).
    // Создаётся вручную в @BeforeEach: НЕ через @InjectMocks.
    // Причина: NotificationConsumer требует TWO конструктора зависимости (Service + ObjectMapper).
    // @InjectMocks с двумя зависимостями работает, но ObjectMapper не @Mock —
    //   реальный ObjectMapper нужен для парсинга JSON (мок ObjectMapper не парсит).
    private NotificationConsumer consumer;

    // @BeforeEach — создаём consumer с реальным ObjectMapper перед каждым тестом.
    @BeforeEach
    void setUp() {
        // ObjectMapper — создаём реальный (не мок): он должен реально парсить JSON строки.
        // new ObjectMapper() — стандартный Jackson mapper, понимает простые POJO.
        consumer = new NotificationConsumer(notificationService, new ObjectMapper());
    }

    // ================================================================
    // handleTicketPurchase тесты
    // ================================================================

    @Test
    void handleTicketPurchase_validMessage_createsNotification() {
        // Text block (Java 13+): строка с отступами для удобочитаемости.
        // Содержит валидный JSON TicketPurchaseEvent.
        // orderId=1, userId=42, movieTitle="Интерстеллар", sessionTime, totalPrice
        String message = """
                {"orderId":1,"userId":42,"movieTitle":"Интерстеллар","sessionTime":"2026-05-10 15:00","totalPrice":450.00}
                """;

        // Act: имитируем получение Kafka сообщения
        consumer.handleTicketPurchase(message);

        // Assert: NotificationService.createNotification() вызван с правильными аргументами
        verify(notificationService).createNotification(
                eq(42L),              // userId из события
                contains("куплен"),  // заголовок содержит "куплен"
                contains("Интерстеллар")  // content содержит название фильма
        );
    }

    @Test
    void handleTicketPurchase_invalidJson_doesNotThrowAndDoesNotCallService() {
        // Некорректный JSON — ObjectMapper.readValue() бросит исключение.
        // Consumer должен поймать его в try-catch и НЕ пробрасывать наружу.
        // assertThatCode — проверяем что блок кода НЕ бросает никаких исключений.
        assertThatCode(() -> consumer.handleTicketPurchase("invalid-json{{{"))
                .doesNotThrowAnyException();

        // NotificationService НЕ вызывался (до него не дошли из-за ошибки парсинга)
        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void handleTicketPurchase_containsSessionTimeAndPrice() {
        // Тест с другим фильмом и временем — проверяем что поля корректно парсятся
        String message = """
                {"orderId":2,"userId":7,"movieTitle":"Дюна","sessionTime":"2026-06-01 18:30","totalPrice":600.00}
                """;

        consumer.handleTicketPurchase(message);

        // Проверяем точный заголовок ("Билет куплен!") и что content содержит название фильма
        verify(notificationService).createNotification(
                eq(7L),
                eq("Билет куплен!"),  // точное значение из NotificationConsumer
                contains("Дюна")     // content содержит название фильма
        );
    }

    // ================================================================
    // handleSupportMessage тесты
    // ================================================================

    @Test
    void handleSupportMessage_validMessage_createsNotificationForRecipient() {
        // Событие от support-service: тикет 5, отправитель 1, получатель 99
        String message = """
                {"ticketId":5,"senderId":1,"content":"Привет!","recipientId":99}
                """;

        consumer.handleSupportMessage(message);

        // Assert: уведомление создано для ПОЛУЧАТЕЛЯ (recipientId=99), не отправителя
        verify(notificationService).createNotification(
                eq(99L),               // recipientId из события
                contains("поддержке"), // заголовок содержит "поддержке"
                eq("Привет!")          // content = текст сообщения из события
        );
    }

    @Test
    void handleSupportMessage_invalidJson_doesNotThrowAndDoesNotCallService() {
        // Некорректный JSON — consumer поглощает исключение
        assertThatCode(() -> consumer.handleSupportMessage("not-valid"))
                .doesNotThrowAnyException();

        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    @Test
    void handleSupportMessage_missingRecipientId_handledGracefully() {
        // JSON без поля recipientId → Jackson десериализует recipientId как null.
        // consumer.handleSupportMessage() вызывает notificationService.createNotification(null, ...).
        // Если сервис не обрабатывает null — может бросить NPE или сохранить NULL.
        // Consumer обернёт любую ошибку в try-catch → не пробрасывает наружу.
        String message = """
                {"ticketId":5,"senderId":1,"content":"Тест"}
                """;

        // Не должно бросить исключение (try-catch в consumer)
        assertThatCode(() -> consumer.handleSupportMessage(message))
                .doesNotThrowAnyException();
        // Не проверяем вызов сервиса — он мог быть вызван с null userId или не вызван вообще.
    }
}
