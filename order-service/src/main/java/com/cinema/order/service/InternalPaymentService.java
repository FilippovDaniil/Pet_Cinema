package com.cinema.order.service;

import com.cinema.dto.order.PaymentWebhookRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

// @Slf4j — Lombok: генерирует поле log
@Slf4j
// @Service — Spring управляет этим бином; инжектируется в OrderService
@Service
public class InternalPaymentService {

    // Используем plainRestTemplate (НЕ @LoadBalanced), так как делаем self-call.
    // @LoadBalanced RestTemplate пытается резолвить hostname через Eureka:
    //   "order-service" → ищет в реестре Eureka сервис с именем "order-service".
    //   Это работает, но нам проще использовать прямой URL из конфига.
    // @Qualifier("plainRestTemplate") — явно указываем нужный бин
    //   (без @Qualifier Spring инжектирует @Primary — тот самый @LoadBalanced).
    private final RestTemplate restTemplate;

    // Конструктор вместо @Autowired + @Qualifier на поле — лучшая практика (явная инъекция)
    public InternalPaymentService(@Qualifier("plainRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // URL вебхука из application.yml: payment.webhook-url
    // В Docker: "http://order-service:8084/api/orders/webhook/payment" (Docker DNS)
    // В тестах: "http://localhost:8084/api/orders/webhook/payment"
    @Value("${payment.webhook-url}")
    private String webhookUrl;

    // @Async("taskExecutor") — метод выполняется асинхронно в пуле "taskExecutor" (из AsyncConfig).
    // OrderService вызывает simulatePayment() после сохранения заказа — управление возвращается
    // немедленно, а сам метод продолжает работу в отдельном потоке "OrderAsync-N".
    // Это позволяет вернуть ответ клиенту (OrderDto с PENDING статусом) без ожидания оплаты.
    //
    // ВАЖНО: @Async работает только через прокси Spring.
    // Вызов this.simulatePayment() внутри класса НЕ будет асинхронным — нет прокси.
    // Обязательно вызывать через Spring-managed бин (как OrderService делает через инъекцию).
    @Async("taskExecutor")
    public void simulatePayment(Long orderId) {
        try {
            log.info("Starting payment simulation for order {}", orderId);

            // Имитируем задержку обработки платежа — 5 секунд (реальный платёжный шлюз работает быстрее).
            // В реальной системе здесь был бы вызов платёжного API (Stripe, Tinkoff, etc.)
            Thread.sleep(5000);

            // Формируем запрос вебхука — симулируем всегда успешный платёж (учебный проект)
            PaymentWebhookRequest webhookRequest = PaymentWebhookRequest.builder()
                    .orderId(orderId)
                    .status("SUCCESS")                          // всегда SUCCESS в симуляции
                    .transactionId(UUID.randomUUID().toString()) // уникальный ID транзакции
                    .build();

            // POST на вебхук-эндпоинт order-service (self-call).
            // Это тот же сервис, что и мы сами — но через HTTP (имитация внешнего платёжного шлюза).
            // В реальной системе этот вызов пришёл бы от внешнего сервиса Tinkoff/Stripe.
            restTemplate.postForObject(webhookUrl, webhookRequest, Void.class);
            log.info("Payment simulation completed for order {}, status=SUCCESS", orderId);

        } catch (InterruptedException e) {
            // InterruptedException: поток прерван (например, при shutdown приложения).
            // Thread.currentThread().interrupt() — восстанавливаем флаг прерывания потока.
            // Это важно: проглотить InterruptedException без restore — антипаттерн.
            Thread.currentThread().interrupt();
            log.error("Payment simulation interrupted for order {}", orderId);
        } catch (Exception e) {
            // Любые другие ошибки (network, HTTP 5xx от вебхука) — логируем, не пробрасываем.
            // @Async методы не могут пробросить исключение вызывающему (разные потоки).
            // В учебном проекте просто логируем; в prod нужна retry-логика.
            log.error("Payment simulation failed for order {}: {}", orderId, e.getMessage());
        }
    }
}
