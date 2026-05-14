package com.cinema.support;

import com.cinema.dto.support.SupportMessageDto;
import com.cinema.dto.support.SupportMessageRequest;
import com.cinema.dto.support.SupportTicketCreateRequest;
import com.cinema.dto.support.SupportTicketDto;
import com.cinema.support.exception.AccessDeniedException;
import com.cinema.support.exception.ResourceNotFoundException;
import com.cinema.support.service.SupportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @SpringBootTest — поднимает ПОЛНЫЙ Spring ApplicationContext (JPA, Security, все сервисы/репозитории).
// properties — переопределяем конфигурацию для тестов:
//   eureka.client.enabled=false — не пытаемся подключиться к Eureka (он не запущен в тестах)
//   spring.cloud.discovery.enabled=false — дополнительно отключаем Cloud Discovery
//   spring.autoconfigure.exclude=...KafkaAutoConfiguration — ПОЛНОСТЬЮ отключаем Kafka автоконфигурацию.
//     Это отличие от order-service: там @MockBean KafkaTemplate было достаточно.
//     Здесь исключаем KafkaAutoConfiguration — Spring даже не пытается подключиться к брокеру.
//     Kafka не нужен: @MockBean KafkaTemplate перехватывает все вызовы kafkaTemplate.send().
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
// @ActiveProfiles("test") — загружает src/test/resources/application-test.yml.
// Переопределяет datasource (TC JDBC URL), kafka, jwt.secret, ddl-auto=create-drop.
@ActiveProfiles("test")
// @Testcontainers — JUnit 5 расширение для управления Docker контейнерами.
// Автоматически запускает @Container поля и останавливает после тестов.
@Testcontainers
class SupportServiceIntegrationTest {

    // @Container static — один PostgreSQL контейнер на весь тестовый класс.
    // static: запускается ОДИН РАЗ до первого теста, останавливается после последнего.
    // postgres:15-alpine — лёгкий образ PostgreSQL 15 (alpine = минимальный размер).
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("support_db_test")  // имя тестовой БД
            .withUsername("cinema")               // пользователь (как в продакшн)
            .withPassword("cinema");              // пароль

    // @DynamicPropertySource — устанавливает Spring properties ДО создания ApplicationContext.
    // Необходим потому что порт PostgreSQL в Testcontainers ДИНАМИЧЕСКИЙ (случайный free port).
    // Без этого Spring попытается подключиться по URL из application-test.yml (TC-драйвер URL),
    // но @DynamicPropertySource переопределяет на реальный порт запущенного контейнера.
    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);        // реальный JDBC URL контейнера
        registry.add("spring.datasource.username", postgres::getUsername);   // "cinema"
        registry.add("spring.datasource.password", postgres::getPassword);   // "cinema"
        // Переопределяем драйвер: явный стандартный PostgreSQL драйвер вместо TC-драйвера.
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    // @MockBean KafkaTemplate — заглушка Kafka.
    // Все вызовы kafkaTemplate.send() в SupportService перехватываются Mockito (ничего не делают).
    // Реальный Kafka брокер не нужен — мы проверяем только логику сервиса.
    @MockBean
    KafkaTemplate<String, Object> kafkaTemplate;

    // Реальный SupportService из Spring Context — тестируем с реальной PostgreSQL.
    @Autowired
    SupportService supportService;

    // Репозитории для прямой работы с БД (очистка данных в @BeforeEach, проверки).
    @Autowired
    com.cinema.support.repository.SupportTicketRepository ticketRepository;

    @Autowired
    com.cinema.support.repository.SupportMessageRepository messageRepository;

    // @BeforeEach — очистка БД перед каждым тестом для изоляции.
    // Порядок важен: сначала messages (зависит от tickets FK), затем tickets.
    @BeforeEach
    void cleanup() {
        messageRepository.deleteAll();  // сначала дочерняя таблица (FK constraint)
        ticketRepository.deleteAll();   // затем родительская
    }

    // ================================================================
    // Тест: создание тикета — персистентность и корректность DTO
    // ================================================================

    @Test
    void createTicket_persistedAndRetrievable() {
        // Act: создаём тикет через реальный сервис → реальная транзакция PostgreSQL
        SupportTicketDto dto = supportService.createTicket(
                new SupportTicketCreateRequest("Проблема с заказом"), 1L);

        // Assert: DTO корректен
        assertThat(dto.getId()).isNotNull();              // id назначен PostgreSQL (INSERT успешен)
        assertThat(dto.getClientId()).isEqualTo(1L);     // clientId сохранён правильно
        assertThat(dto.getSubject()).isEqualTo("Проблема с заказом");
        assertThat(dto.getStatus()).isEqualTo("OPEN");   // начальный статус
        assertThat(dto.getCreatedAt()).isNotNull();       // timestamp установлен
    }

    // ================================================================
    // Тест: getMyTickets — фильтрация по clientId
    // ================================================================

    @Test
    void getMyTickets_returnsOnlyClientTickets() {
        // Arrange: создаём 3 тикета: 2 от клиента 1L, 1 от клиента 2L
        supportService.createTicket(new SupportTicketCreateRequest("Тикет 1"), 1L);
        supportService.createTicket(new SupportTicketCreateRequest("Тикет 2"), 1L);
        supportService.createTicket(new SupportTicketCreateRequest("Чужой тикет"), 2L);

        // Act: запрашиваем тикеты клиента 1L
        List<SupportTicketDto> myTickets = supportService.getMyTickets(1L);

        // Assert: только 2 тикета клиента 1L, не 3 всего
        assertThat(myTickets).hasSize(2);
        // allMatch — все элементы соответствуют условию: clientId = 1L
        assertThat(myTickets).allMatch(t -> t.getClientId().equals(1L));
    }

    // ================================================================
    // Тест: getAllTickets — возвращает все тикеты
    // ================================================================

    @Test
    void getAllTickets_returnsAll() {
        // Arrange: 2 тикета от разных клиентов
        supportService.createTicket(new SupportTicketCreateRequest("T1"), 1L);
        supportService.createTicket(new SupportTicketCreateRequest("T2"), 2L);

        // Act: администратор запрашивает все тикеты
        List<SupportTicketDto> all = supportService.getAllTickets();

        assertThat(all).hasSize(2);  // оба тикета возвращены
    }

    // ================================================================
    // Тест: полный диалог — sendMessage + getMessages
    // ================================================================

    @Test
    void sendMessage_andGetMessages_flow() {
        // Arrange: создаём тикет
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Вопрос"), 1L);

        // Клиент отправляет первое сообщение (до назначения admin — adminId=null)
        supportService.sendMessage(ticket.getId(),
                new SupportMessageRequest("Привет, мне нужна помощь!"), 1L, "CLIENT");

        // Администратор отвечает (role="ADMIN" — доступ к любому тикету)
        supportService.sendMessage(ticket.getId(),
                new SupportMessageRequest("Конечно, чем помочь?"), 99L, "ADMIN");

        // Назначаем admin ПОСЛЕ отправки сообщений (демонстрация что порядок гибкий)
        // В реальном сценарии: сначала assign, потом sendMessage
        supportService.assignAdmin(ticket.getId(), 99L);

        // Act: клиент читает сообщения своего тикета
        List<SupportMessageDto> messages = supportService.getMessages(ticket.getId(), 1L, "CLIENT");

        // Assert: оба сообщения сохранены в БД и возвращены в правильном порядке
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("Привет, мне нужна помощь!");
        assertThat(messages.get(1).getContent()).isEqualTo("Конечно, чем помочь?");
    }

    // ================================================================
    // Тест: чужой клиент не может отправить сообщение
    // ================================================================

    @Test
    void sendMessage_unauthorizedUser_throwsAccessDenied() {
        // Тикет создан клиентом 1L
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Тикет клиента 1"), 1L);

        // Другой клиент (userId=2L) пытается написать в чужой тикет
        // isOwner=false (2 ≠ 1), isAdmin=false (role="CLIENT") → AccessDeniedException
        assertThatThrownBy(() ->
                supportService.sendMessage(ticket.getId(),
                        new SupportMessageRequest("Чужое сообщение"), 2L, "CLIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ================================================================
    // Тест: чужой клиент не может читать сообщения
    // ================================================================

    @Test
    void getMessages_unauthorizedUser_throwsAccessDenied() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Мой тикет"), 1L);

        // userId=2L пытается читать тикет клиента 1L
        assertThatThrownBy(() ->
                supportService.getMessages(ticket.getId(), 2L, "CLIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ================================================================
    // Тест: назначение администратора
    // ================================================================

    @Test
    void assignAdmin_updatesTicket() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Нужна помощь"), 1L);

        // Перед назначением adminId = null
        assertThat(ticket.getAdminId()).isNull();

        // Act: назначаем admin=42L
        SupportTicketDto updated = supportService.assignAdmin(ticket.getId(), 42L);

        // Assert: adminId обновлён, статус остался OPEN
        assertThat(updated.getAdminId()).isEqualTo(42L);
        assertThat(updated.getStatus()).isEqualTo("OPEN");  // закрытие отдельной операцией
    }

    // ================================================================
    // Тест: закрытие тикета
    // ================================================================

    @Test
    void closeTicket_changesStatus() {
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Закрыть меня"), 1L);

        // Act: администратор закрывает тикет
        SupportTicketDto closed = supportService.closeTicket(ticket.getId());

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
    }

    // ================================================================
    // Тест: полный жизненный цикл тикета (assign + close)
    // ================================================================

    @Test
    void assignAndClose_fullFlow() {
        // Arrange
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Полный флоу"), 1L);

        // Act: назначить → закрыть
        supportService.assignAdmin(ticket.getId(), 10L);
        SupportTicketDto closed = supportService.closeTicket(ticket.getId());

        // Assert: и статус закрыт, и adminId сохранился
        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getAdminId()).isEqualTo(10L);
    }

    // ================================================================
    // Тест: ошибка при отправке сообщения в несуществующий тикет
    // ================================================================

    @Test
    void sendMessage_ticketNotFound_throwsResourceNotFound() {
        // ticketId=999 не существует в БД
        assertThatThrownBy(() ->
                supportService.sendMessage(999L,
                        new SupportMessageRequest("Сообщение"), 1L, "CLIENT"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================
    // Тест: ошибка при закрытии несуществующего тикета
    // ================================================================

    @Test
    void closeTicket_notFound_throwsResourceNotFound() {
        assertThatThrownBy(() -> supportService.closeTicket(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ================================================================
    // Тест: ADMIN читает сообщения любого тикета
    // ================================================================

    @Test
    void adminCanReadAnyTicketMessages() {
        // Тикет создан клиентом 1L, сообщение отправлено клиентом
        SupportTicketDto ticket = supportService.createTicket(
                new SupportTicketCreateRequest("Тикет"), 1L);
        supportService.sendMessage(ticket.getId(),
                new SupportMessageRequest("Вопрос"), 1L, "CLIENT");

        // ADMIN userId=99 (не владелец тикета) может читать
        // isAdmin=true (role="ADMIN") → доступ разрешён независимо от clientId
        List<SupportMessageDto> messages = supportService.getMessages(
                ticket.getId(), 99L, "ADMIN");

        assertThat(messages).hasSize(1);  // одно сообщение доступно администратору
    }
}
