package com.cinema.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequestConsumer {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${order.webhook-url}")
    private String webhookUrl;

    @KafkaListener(topics = "payment-request", groupId = "payment-simulator-group")
    public void handlePaymentRequest(String message) {
        try {
            JsonNode payload = objectMapper.readTree(message);
            Long orderId = payload.get("orderId").asLong();
            Long userId = payload.get("userId").asLong();
            String amount = payload.has("amount") ? payload.get("amount").asText() : "0";

            log.info("Received payment request: orderId={}, userId={}, amount={}", orderId, userId, amount);

            processPaymentAsync(orderId);
        } catch (Exception e) {
            log.error("Failed to process payment-request message: {}", e.getMessage(), e);
        }
    }

    @Async("paymentTaskExecutor")
    public void processPaymentAsync(Long orderId) {
        try {
            log.info("Processing payment for orderId={}, simulating delay...", orderId);
            Thread.sleep(3000);

            String transactionId = UUID.randomUUID().toString();

            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("orderId", orderId);
            webhookPayload.put("status", "SUCCESS");
            webhookPayload.put("transactionId", transactionId);

            log.info("Sending payment result to webhook: orderId={}, transactionId={}", orderId, transactionId);
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, webhookPayload, String.class);
            log.info("Webhook response for orderId={}: status={}", orderId, response.getStatusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Payment processing interrupted for orderId={}", orderId);
        } catch (Exception e) {
            log.error("Failed to send payment webhook for orderId={}: {}", orderId, e.getMessage(), e);
        }
    }
}
