package com.cinema.support.config;

import com.cinema.support.entity.SupportMessage;
import com.cinema.support.entity.SupportTicket;
import com.cinema.support.entity.TicketStatus;
import com.cinema.support.repository.SupportMessageRepository;
import com.cinema.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// @Slf4j — Lombok: Logger log для info/warn логирования.
@Slf4j
// @Component — Spring управляет жизненным циклом бина.
// @Component (не @Configuration) — DataLoader не содержит @Bean методы,
//   просто реализует CommandLineRunner.
@Component
// @RequiredArgsConstructor — конструктор для final полей (repositories).
@RequiredArgsConstructor
// CommandLineRunner — интерфейс Spring Boot.
// Метод run() вызывается ПОСЛЕ полного запуска ApplicationContext (после JPA, Kafka, Security).
// Используется для наполнения БД демо-данными при первом старте.
public class DataLoader implements CommandLineRunner {

    // Репозитории для работы с БД (инжектируются через конструктор).
    private final SupportTicketRepository supportTicketRepository;
    private final SupportMessageRepository supportMessageRepository;

    // Константы id пользователей из auth-service DataLoader:
    // client1 имеет id=1, admin1 имеет id=2 (первые созданные пользователи).
    // Используем Long константы чтобы избежать повторения магических чисел.
    private static final Long DEMO_CLIENT_ID = 1L;   // client1 из auth-service
    private static final Long DEMO_ADMIN_ID = 2L;    // admin1 из auth-service (условно seller1 или admin1)

    // run() — точка входа CommandLineRunner.
    // String... args — аргументы командной строки (обычно не используются в DataLoader).
    @Override
    public void run(String... args) {
        // Идемпотентность: загружаем данные ТОЛЬКО если таблица пуста.
        // count() == 0 — ни одного тикета в БД.
        // Это предотвращает дублирование при перезапуске сервиса
        //   (БД persistent → данные уже есть при повторном старте).
        if (supportTicketRepository.count() == 0) {
            log.info("Loading initial support data...");

            LocalDateTime now = LocalDateTime.now();

            // Создаём демо-тикет.
            // adminId = null — тикет создан, но администратор ещё не назначен.
            // status = OPEN — новый тикет всегда открыт.
            SupportTicket ticket = SupportTicket.builder()
                    .clientId(DEMO_CLIENT_ID)              // клиент userId=1 (client1)
                    .adminId(null)                          // нет назначенного администратора
                    .subject("Помогите с оформлением заказа")  // тема обращения
                    .status(TicketStatus.OPEN)             // статус: открыт
                    .createdAt(now)                        // явно задаём время (не через @PrePersist)
                    .updatedAt(now)
                    .build();

            // Сохраняем тикет в БД. PostgreSQL назначает id (SERIAL).
            SupportTicket savedTicket = supportTicketRepository.save(ticket);

            // Первое сообщение: клиент задаёт вопрос.
            // ticketId = savedTicket.getId() — используем реальный id из БД.
            SupportMessage clientMessage = SupportMessage.builder()
                    .ticketId(savedTicket.getId())          // FK: к только что созданному тикету
                    .senderId(DEMO_CLIENT_ID)               // отправитель: клиент userId=1
                    .content("Здравствуйте! Не могу оформить заказ на билет, при оплате выдаёт ошибку. Что делать?")
                    .sentAt(now)                            // время отправки = сейчас
                    .build();

            // Второе сообщение: ответ администратора (условно — через 5 минут).
            // Это демонстрирует диалог в интерфейсе поддержки.
            SupportMessage adminMessage = SupportMessage.builder()
                    .ticketId(savedTicket.getId())          // тот же тикет
                    .senderId(DEMO_ADMIN_ID)                // отправитель: admin userId=2
                    .content("Здравствуйте! Попробуйте очистить кэш браузера и повторить попытку. Если проблема сохраняется, напишите нам ещё раз.")
                    .sentAt(now.plusMinutes(5))             // ответ через 5 минут после вопроса
                    .build();

            // Сохраняем оба сообщения в БД.
            // @PrePersist НЕ перезаписывает sentAt (if sentAt == null) — мы задали явно.
            supportMessageRepository.save(clientMessage);
            supportMessageRepository.save(adminMessage);

            log.info("Demo support ticket #{} created with 2 messages", savedTicket.getId());
        }
        // Если count() > 0 — данные уже есть, ничего не делаем (тихий пропуск).
    }
}
