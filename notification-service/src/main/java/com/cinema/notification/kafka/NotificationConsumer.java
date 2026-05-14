package com.cinema.notification.kafka;

import com.cinema.dto.event.SupportMessageEvent;
import com.cinema.dto.event.TicketPurchaseEvent;
import com.cinema.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// @Slf4j — Lombok: Logger для логирования успешной обработки и ошибок.
@Slf4j
// @Component — Spring регистрирует этот класс как бин и запускает @KafkaListener методы.
@Component
// @RequiredArgsConstructor — Lombok: конструктор для final полей (notificationService, objectMapper).
@RequiredArgsConstructor
// NotificationConsumer — Kafka consumer для notification-service.
// Подписывается на два топика:
//   "ticket-purchase" — событие о покупке билета (от order-service)
//   "support-message" — событие о новом сообщении в поддержке (от support-service)
//
// ВАЖНАЯ ОСОБЕННОСТЬ: String-десериализация + ручной парсинг JSON через ObjectMapper.
// В отличие от order-service (JsonDeserializer), здесь:
//   value-deserializer = StringDeserializer → @KafkaListener получает String
//   ObjectMapper.readValue(message, EventClass.class) → конвертируем в объект
// Плюсы: гибкость (разные типы событий из разных сервисов), устойчивость к schema changes.
// Минусы: нет типобезопасности на уровне Kafka (ошибки обнаруживаются только при парсинге).
public class NotificationConsumer {

    // notificationService — создаёт уведомления в PostgreSQL.
    private final NotificationService notificationService;

    // objectMapper — Jackson ObjectMapper для десериализации JSON строки в POJO.
    // Автоматически инжектируется Spring Boot (Jackson AutoConfiguration создаёт @Bean ObjectMapper).
    private final ObjectMapper objectMapper;

    // ================================================================
    // Listener: "ticket-purchase" топик
    // ================================================================

    // @KafkaListener — регистрирует этот метод как Kafka consumer.
    //   topics = "ticket-purchase" — слушаем этот топик.
    //   groupId = "notification-group" — consumer group ID.
    //     Consumer Group: Kafka распределяет партиции между членами группы.
    //     Если несколько экземпляров notification-service запущены — Kafka балансирует нагрузку.
    //     Если сообщение уже обработано одним экземпляром группы — другие не получат его.
    @KafkaListener(topics = "ticket-purchase", groupId = "notification-group")
    public void handleTicketPurchase(String message) {
        // try-catch вокруг всего метода — КРИТИЧНО для Kafka consumer!
        // Без try-catch: если парсинг JSON упадёт с исключением → Kafka перезапустит обработку
        //   (retry loop) → бесконечные повторы плохого сообщения → dead letter queue или блокировка.
        // С try-catch: логируем ошибку и продолжаем (at-least-once, возможна потеря уведомления).
        try {
            // objectMapper.readValue — десериализует JSON строку в TicketPurchaseEvent.
            // TicketPurchaseEvent содержит: orderId, userId, movieTitle, sessionTime, totalPrice.
            // Если JSON некорректен или нет нужных полей → Jackson бросает Exception → catch блок.
            TicketPurchaseEvent event = objectMapper.readValue(message, TicketPurchaseEvent.class);

            // Формируем текст уведомления из данных события.
            // String.format — классический printf-стиль форматирования строки.
            // %s — подставляет строковое значение (movieTitle, sessionTime, totalPrice).
            String content = String.format(
                    "Ваш билет на %s приобретён. Сеанс: %s. Сумма: %s",
                    event.getMovieTitle(),    // название фильма из event
                    event.getSessionTime(),   // время сеанса из event
                    event.getTotalPrice()     // сумма покупки из event
            );

            // Создаём уведомление в БД для пользователя который купил билет.
            // event.getUserId() — id клиента, который совершил покупку.
            notificationService.createNotification(event.getUserId(), "Билет куплен!", content);
            log.info("Processed ticket-purchase event for userId={}, orderId={}", event.getUserId(), event.getOrderId());

        } catch (Exception e) {
            // Логируем полный stacktrace (e.getMessage() и e) — для отладки.
            // Сообщение "проглатывается" — Kafka считает offset обработанным (commit).
            // Это "at-most-once" семантика для ошибочных сообщений.
            log.error("Failed to process ticket-purchase message: {}", e.getMessage(), e);
        }
    }

    // ================================================================
    // Listener: "support-message" топик
    // ================================================================

    @KafkaListener(topics = "support-message", groupId = "notification-group")
    public void handleSupportMessage(String message) {
        try {
            // Десериализуем JSON в SupportMessageEvent.
            // SupportMessageEvent содержит: ticketId, senderId, content, recipientId.
            // recipientId — кому отправить уведомление (определяется в support-service).
            SupportMessageEvent event = objectMapper.readValue(message, SupportMessageEvent.class);

            // Создаём уведомление для ПОЛУЧАТЕЛЯ сообщения (не отправителя!).
            // event.getRecipientId() — это:
            //   - adminId если сообщение от клиента (CLIENT → уведомить ADMIN)
            //   - clientId если сообщение от администратора (ADMIN → уведомить CLIENT)
            // event.getContent() — текст самого сообщения поддержки (используем как content уведомления).
            notificationService.createNotification(
                    event.getRecipientId(),              // кому уведомление
                    "Новое сообщение в поддержке",       // заголовок (фиксированный)
                    event.getContent()                   // текст = содержание сообщения
            );
            log.info("Processed support-message event for recipientId={}, ticketId={}", event.getRecipientId(), event.getTicketId());

        } catch (Exception e) {
            log.error("Failed to process support-message message: {}", e.getMessage(), e);
        }
    }
}
