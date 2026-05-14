package com.cinema.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

// @Configuration — помечает класс как источник @Bean определений.
@Configuration
public class RestTemplateConfig {

    // @Bean — регистрирует RestTemplate в ApplicationContext.
    // RestTemplate — синхронный HTTP клиент Spring для вызовов внешних сервисов.
    //
    // ВАЖНО: здесь НЕ используется @LoadBalanced (в отличие от order-service).
    // @LoadBalanced добавляет Ribbon/Spring Cloud LoadBalancer interceptor,
    // позволяющий использовать lb://service-name URL.
    //
    // payment-simulator вызывает order-service по ПРЯМОМУ URL из конфига:
    //   order.webhook-url: http://order-service:8084/api/orders/webhook/payment
    // В Docker Compose имя контейнера "order-service" резолвится через Docker DNS —
    // @LoadBalanced не нужен, достаточно прямого HTTP вызова.
    @Bean
    public RestTemplate restTemplate() {
        // new RestTemplate() — создаёт стандартный RestTemplate с дефолтными настройками:
        //   - конвертеры: JSON (Jackson), XML, String, byte[] и др.
        //   - таймаут: по умолчанию нет (бесконечное ожидание) — для симулятора приемлемо
        //   - нет retry логики — при ошибке вебхука просто логируется ошибка
        return new RestTemplate();
    }
}
