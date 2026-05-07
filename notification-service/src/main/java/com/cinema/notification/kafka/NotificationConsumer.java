package com.cinema.notification.kafka;

import com.cinema.dto.event.SupportMessageEvent;
import com.cinema.dto.event.TicketPurchaseEvent;
import com.cinema.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ticket-purchase", groupId = "notification-group")
    public void handleTicketPurchase(String message) {
        try {
            TicketPurchaseEvent event = objectMapper.readValue(message, TicketPurchaseEvent.class);
            String content = String.format(
                    "Ваш билет на %s приобретён. Сеанс: %s. Сумма: %s",
                    event.getMovieTitle(),
                    event.getSessionTime(),
                    event.getTotalPrice()
            );
            notificationService.createNotification(event.getUserId(), "Билет куплен!", content);
            log.info("Processed ticket-purchase event for userId={}, orderId={}", event.getUserId(), event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process ticket-purchase message: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "support-message", groupId = "notification-group")
    public void handleSupportMessage(String message) {
        try {
            SupportMessageEvent event = objectMapper.readValue(message, SupportMessageEvent.class);
            notificationService.createNotification(
                    event.getRecipientId(),
                    "Новое сообщение в поддержке",
                    event.getContent()
            );
            log.info("Processed support-message event for recipientId={}, ticketId={}", event.getRecipientId(), event.getTicketId());
        } catch (Exception e) {
            log.error("Failed to process support-message message: {}", e.getMessage(), e);
        }
    }
}
