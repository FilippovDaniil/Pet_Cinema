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

// @Slf4j — Lombok генерирует поле: private static final Logger log = LoggerFactory.getLogger(PaymentRequestConsumer.class)
// Используется для log.info(), log.error() — отслеживаем все шаги обработки платежа
@Slf4j

// @Component — регистрирует класс как Spring бин (не @Service, @Controller — просто компонент).
// Spring создаёт единственный экземпляр (singleton) при старте.
@Component

// @RequiredArgsConstructor — Lombok генерирует конструктор для всех final полей:
//   PaymentRequestConsumer(ObjectMapper objectMapper, RestTemplate restTemplate)
// Spring использует этот конструктор для Dependency Injection.
@RequiredArgsConstructor
public class PaymentRequestConsumer {

    // ObjectMapper — Jackson JSON парсер, инжектируется Spring Boot AutoConfiguration бином.
    // Используется для парсинга входящего JSON из Kafka топика "payment-request".
    // ВАЖНО: используем JsonNode (дерево), а не полный DTO — для гибкого парсинга
    // (если в событии появятся новые поля — код не сломается).
    private final ObjectMapper objectMapper;

    // RestTemplate — HTTP клиент для отправки вебхука в order-service.
    // Бин создан в RestTemplateConfig.java (без @LoadBalanced — прямой URL вызов).
    private final RestTemplate restTemplate;

    // @Value("${order.webhook-url}") — инжектирует значение из application.yml:
    //   order.webhook-url: http://${ORDER_SERVICE_HOST:order-service}:${ORDER_SERVICE_PORT:8084}/api/orders/webhook/payment
    // В Docker Compose: http://order-service:8084/api/orders/webhook/payment
    // Конфигурируемый URL позволяет легко менять адрес order-service без пересборки.
    @Value("${order.webhook-url}")
    private String webhookUrl;

    // @KafkaListener — Spring Kafka слушает топик "payment-request".
    // topics = "payment-request" — имя топика (создаётся в order-service KafkaConfig).
    // groupId = "payment-simulator-group" — consumer group.
    //   Все экземпляры с одним groupId делят партиции топика (load balancing).
    //   У нас один экземпляр — он получает ВСЕ сообщения из топика.
    // String message — тип параметра, соответствует value-deserializer: StringDeserializer из application.yml.
    //   Сообщение приходит как сырая JSON строка.
    @KafkaListener(topics = "payment-request", groupId = "payment-simulator-group")
    public void handlePaymentRequest(String message) {
        try {
            // objectMapper.readTree(message) — парсит JSON строку в JsonNode дерево.
            // JsonNode — универсальное дерево узлов Jackson.
            // Альтернатива: objectMapper.readValue(message, PaymentRequestEvent.class) —
            //   но JsonNode гибче: не нужен класс DTO, легко добавить новые поля.
            JsonNode payload = objectMapper.readTree(message);

            // payload.get("orderId").asLong() — извлекаем поле "orderId" как Long.
            // asLong() — конвертирует JsonNode в long (если null → 0L).
            // orderId — уникальный идентификатор заказа из order-service.
            Long orderId = payload.get("orderId").asLong();

            // userId и amount — дополнительные поля для логирования (не используются в логике).
            Long userId = payload.get("userId").asLong();

            // payload.has("amount") — проверяем наличие поля (избегаем NPE если поле отсутствует).
            // payload.get("amount").asText() — получаем как строку (amount = BigDecimal → JSON number).
            String amount = payload.has("amount") ? payload.get("amount").asText() : "0";

            log.info("Received payment request: orderId={}, userId={}, amount={}", orderId, userId, amount);

            // processPaymentAsync(orderId) — запускаем асинхронную обработку.
            // @Async("paymentTaskExecutor") на processPaymentAsync означает:
            //   Spring создаёт новый поток из пула "paymentTaskExecutor" для выполнения метода.
            //   handlePaymentRequest() НЕМЕДЛЕННО возвращается (не ждёт 3 секунды).
            //   Это важно: Kafka listener не должен блокироваться на долгих операциях —
            //     иначе другие сообщения не обрабатываются пока идёт ожидание.
            processPaymentAsync(orderId);
        } catch (Exception e) {
            // Логируем ошибку (неверный JSON, отсутствующее поле).
            // НЕ пробрасываем исключение — Kafka не будет повторять сообщение (at-most-once).
            log.error("Failed to process payment-request message: {}", e.getMessage(), e);
        }
    }

    // @Async("paymentTaskExecutor") — метод выполняется асинхронно в пуле "paymentTaskExecutor".
    // Spring создаёт AOP прокси: вызов processPaymentAsync() → Spring перехватывает →
    //   отправляет задачу в ThreadPoolTaskExecutor → метод выполняется в отдельном потоке.
    //
    // ОГРАНИЧЕНИЕ: @Async работает только для вызовов ИЗВНЕ класса (через Spring прокси).
    // Вызов processPaymentAsync(orderId) из handlePaymentRequest() — это вызов this.processPaymentAsync(),
    //   НО Spring перехватывает его через прокси (т.к. handlePaymentRequest вызывается через Kafka listener,
    //   а bean — прокси).
    //   Если бы это был вызов private → private внутри класса без Spring прокси — @Async бы не сработал.
    @Async("paymentTaskExecutor")
    public void processPaymentAsync(Long orderId) {
        try {
            log.info("Processing payment for orderId={}, simulating delay...", orderId);

            // Thread.sleep(3000) — имитируем задержку реального платёжного шлюза (3 секунды).
            // Реальный шлюз (Stripe, PayPal) тоже требует время для обработки.
            // sleep() выполняется в потоке из "paymentTaskExecutor" — не блокирует Kafka listener.
            Thread.sleep(3000);

            // UUID.randomUUID().toString() — генерируем уникальный идентификатор транзакции.
            // Формат: "550e8400-e29b-41d4-a716-446655440000" (стандартный UUID v4).
            // Имитирует transaction ID от реального платёжного провайдера.
            String transactionId = UUID.randomUUID().toString();

            // Формируем тело вебхука — Map который Jackson сериализует в JSON.
            // RestTemplate с Jackson конвертером автоматически сериализует Map<String, Object> → JSON.
            Map<String, Object> webhookPayload = new HashMap<>();
            webhookPayload.put("orderId", orderId);         // ID заказа для сопоставления в order-service
            webhookPayload.put("status", "SUCCESS");         // всегда SUCCESS — симулятор не имитирует отказы
            webhookPayload.put("transactionId", transactionId); // уникальный ID транзакции

            log.info("Sending payment result to webhook: orderId={}, transactionId={}", orderId, transactionId);

            // restTemplate.postForEntity(webhookUrl, webhookPayload, String.class) — HTTP POST вызов.
            // webhookUrl = "http://order-service:8084/api/orders/webhook/payment" (из конфига).
            // webhookPayload → JSON тело запроса (Content-Type: application/json).
            // String.class — тип ответа (нам неважно тело ответа, только статус).
            // ResponseEntity содержит HTTP статус, заголовки, и тело ответа.
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, webhookPayload, String.class);

            // Логируем HTTP статус ответа от order-service (ожидаем 200 OK).
            log.info("Webhook response for orderId={}: status={}", orderId, response.getStatusCode());

        } catch (InterruptedException e) {
            // InterruptedException — поток был прерван (например, при shutdown приложения).
            // Thread.currentThread().interrupt() — ОБЯЗАТЕЛЬНО восстанавливаем флаг прерывания.
            //   Правило Java: если поймал InterruptedException, нужно либо пробросить его,
            //   либо вызвать interrupt() — иначе информация о прерывании теряется,
            //   и JVM не сможет корректно завершить работу потока при shutdown.
            Thread.currentThread().interrupt();
            log.error("Payment processing interrupted for orderId={}", orderId);

        } catch (Exception e) {
            // Любая другая ошибка (network, order-service недоступен, timeout).
            // Логируем и продолжаем — нет retry логики (упрощение для учебного проекта).
            // В продакшн системе здесь был бы retry механизм (exponential backoff, DLQ).
            log.error("Failed to send payment webhook for orderId={}: {}", orderId, e.getMessage(), e);
        }
    }
}
