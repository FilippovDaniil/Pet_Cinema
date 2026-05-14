package com.cinema.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// @Configuration — помечает класс как источник @Bean определений.
// Spring обрабатывает его при старте и регистрирует все @Bean методы в ApplicationContext.
@Configuration

// @EnableAsync — дублирование аннотации из PaymentSimulatorApplication.java.
// Это намеренный "belt and suspenders" (подтяжки и ремень) подход:
// гарантирует что @Async включён независимо от порядка загрузки Spring конфигурации.
// На практике достаточно одного места, но дублирование не вредит.
@EnableAsync
public class AsyncConfig {

    // @Bean(name = "paymentTaskExecutor") — регистрирует Executor бин с именем "paymentTaskExecutor".
    // Имя важно: в PaymentRequestConsumer используется @Async("paymentTaskExecutor") —
    // Spring найдёт именно этот пул потоков по имени.
    // Если имя не совпадает или бин отсутствует — @Async упадёт с NoSuchBeanDefinitionException.
    @Bean(name = "paymentTaskExecutor")
    public Executor paymentTaskExecutor() {
        // ThreadPoolTaskExecutor — Spring обёртка над java.util.concurrent.ThreadPoolExecutor.
        // Позволяет удобно настроить пул потоков для асинхронных задач.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // setCorePoolSize(5) — постоянное количество потоков в пуле (всегда живут).
        // Даже если нет задач, 5 потоков существуют и ждут работы.
        // Подходит для payment-simulator: обычно не более 5 одновременных платёжных запросов.
        executor.setCorePoolSize(5);

        // setMaxPoolSize(10) — максимальное количество потоков при высокой нагрузке.
        // Если очередь заполнена И активных потоков < 10 — создаётся новый поток.
        // Потоки сверх corePoolSize уничтожаются после простоя (по умолчанию 60 сек).
        executor.setMaxPoolSize(10);

        // setQueueCapacity(50) — размер очереди задач.
        // Когда все 5 core потоков заняты, задачи накапливаются в очереди (до 50 штук).
        // При заполнении очереди И потоков < max → создаётся новый поток.
        // При заполнении очереди И потоков = max → RejectedExecutionException (задача отклонена).
        executor.setQueueCapacity(50);

        // setThreadNamePrefix("payment-async-") — префикс имён потоков.
        // В логах потоки будут называться "payment-async-1", "payment-async-2" и т.д.
        // Упрощает отладку: сразу видно что это поток из пула payment-simulator.
        executor.setThreadNamePrefix("payment-async-");

        // initialize() — ОБЯЗАТЕЛЬНЫЙ вызов для ThreadPoolTaskExecutor.
        // Создаёт внутренний ThreadPoolExecutor (java.util.concurrent).
        // Без вызова initialize() бин создастся, но выполнение задач упадёт с NPE.
        executor.initialize();

        // Возвращаем Executor (интерфейс) — Spring @Async использует этот интерфейс.
        return executor;
    }
}
