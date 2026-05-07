package com.cinema.order.service;

import com.cinema.dto.order.PaymentWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Service
public class InternalPaymentService {

    private final RestTemplate restTemplate;

    public InternalPaymentService(@Qualifier("plainRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${payment.webhook-url}")
    private String webhookUrl;

    @Async("taskExecutor")
    public void simulatePayment(Long orderId) {
        try {
            log.info("Starting payment simulation for order {}", orderId);
            Thread.sleep(5000);

            PaymentWebhookRequest webhookRequest = PaymentWebhookRequest.builder()
                    .orderId(orderId)
                    .status("SUCCESS")
                    .transactionId(UUID.randomUUID().toString())
                    .build();

            restTemplate.postForObject(webhookUrl, webhookRequest, Void.class);
            log.info("Payment simulation completed for order {}, status=SUCCESS", orderId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Payment simulation interrupted for order {}", orderId);
        } catch (Exception e) {
            log.error("Payment simulation failed for order {}: {}", orderId, e.getMessage());
        }
    }
}
