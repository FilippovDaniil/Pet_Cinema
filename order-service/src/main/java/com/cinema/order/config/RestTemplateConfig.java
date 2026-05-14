package com.cinema.order.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

// @Configuration — класс содержит определения Bean для HTTP клиентов
@Configuration
public class RestTemplateConfig {

    // ПАТТЕРН: два разных RestTemplate для двух разных сценариев.
    //
    // Проблема: если инжектировать один @LoadBalanced RestTemplate везде,
    // то прямые вызовы (например http://localhost:8084/...) тоже пройдут
    // через load balancer и упадут с ошибкой (lb:// ожидает имя сервиса в Eureka).

    // Bean 1: @LoadBalanced RestTemplate для вызовов через Eureka Service Discovery.
    // @LoadBalanced — оборачивает RestTemplate LoadBalancerInterceptor-ом от Spring Cloud.
    //   При запросе на lb://hall-service/api/sessions/5 интерцептор:
    //   1. Запрашивает у Eureka список инстансов hall-service
    //   2. Выбирает один (Round Robin или случайно)
    //   3. Заменяет lb://hall-service на реальный http://host:port
    // @Primary — этот бин используется по умолчанию при @Autowired RestTemplate
    //   (когда есть несколько кандидатов, @Primary побеждает без @Qualifier)
    @Bean
    @Primary
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Bean 2: обычный RestTemplate для прямых HTTP вызовов без load balancer.
    // Используется в InternalPaymentService для self-call вебхука:
    //   POST http://order-service:8084/api/orders/webhook/payment
    //   (в Docker это container-to-container вызов по имени сервиса — не через Eureka)
    // @Bean(name = "plainRestTemplate") — явное имя, инжектируется через @Qualifier("plainRestTemplate")
    // БЕЗ @LoadBalanced — иначе попытка резолвить "order-service" через Eureka вместо DNS Docker.
    @Bean(name = "plainRestTemplate")
    public RestTemplate plainRestTemplate() {
        return new RestTemplate();
    }
}
