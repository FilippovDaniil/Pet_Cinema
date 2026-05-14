package com.cinema.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// @Configuration — класс содержит определения Spring Beans
@Configuration
// @EnableAsync — активирует поддержку @Async аннотаций.
// Здесь дублирует аннотацию @EnableAsync из OrderServiceApplication — это нормально,
// Spring обрабатывает идемпотентно. Вынесено в отдельный конфиг для явности.
@EnableAsync
public class AsyncConfig {

    // Именованный bean "taskExecutor" — пул потоков для @Async методов.
    // Когда метод помечен @Async("taskExecutor"), Spring выполняет его в этом пуле.
    // Если имя не указать (просто @Async), Spring ищет бин с именем "taskExecutor" по умолчанию.
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Количество потоков, которые всегда живут в пуле (даже если задач нет).
        // 4 — умеренно: не тратим ресурсы впустую, но есть запас для параллельных оплат.
        executor.setCorePoolSize(4);

        // Максимальное количество потоков при пиковой нагрузке.
        // Потоки сверх corePoolSize создаются только когда очередь (queue) заполнена.
        executor.setMaxPoolSize(10);

        // Размер очереди задач. Если все 10 потоков заняты — задача ждёт в очереди.
        // Если очередь тоже заполнена (50 задач) — новые задачи отклоняются с RejectedExecutionException.
        executor.setQueueCapacity(50);

        // Префикс имени потока — видно в логах: "OrderAsync-1", "OrderAsync-2", etc.
        // Упрощает отладку и мониторинг в Grafana/Loki.
        executor.setThreadNamePrefix("OrderAsync-");

        // Инициализация пула — создаёт corePoolSize потоков немедленно.
        // Без вызова initialize() пул создаётся лениво при первом задании.
        executor.initialize();
        return executor;
    }
}
