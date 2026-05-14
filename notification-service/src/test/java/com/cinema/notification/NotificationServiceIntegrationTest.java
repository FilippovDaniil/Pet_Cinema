package com.cinema.notification;

import com.cinema.dto.notification.NotificationDto;
import com.cinema.notification.entity.Notification;
import com.cinema.notification.service.NotificationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @SpringBootTest — полный Spring ApplicationContext (JPA, Security, Service, Repository).
// properties:
//   eureka.client.enabled=false — не подключаемся к Eureka (не запущен).
//   spring.autoconfigure.exclude=...KafkaAutoConfiguration — исключаем Kafka AutoConfiguration.
//     Это ПОЛНОЕ отключение Kafka: Spring даже не пытается подключиться к брокеру.
//     Идентичный подход что в SupportServiceIntegrationTest.
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
// @ActiveProfiles("test") — загружает application-test.yml:
//   TC JDBC URL, create-drop, kafka localhost (не используется), jwt.secret тестовый.
@ActiveProfiles("test")
// @Testcontainers — JUnit 5 расширение управления Docker контейнерами.
@Testcontainers
class NotificationServiceIntegrationTest {

    // @Container static — один PostgreSQL 15 на весь тестовый класс.
    // Запускается один раз (static), останавливается после последнего теста.
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("notification_db_test")
                    .withUsername("cinema")
                    .withPassword("cinema");

    // @DynamicPropertySource — переопределяет datasource URL перед созданием ApplicationContext.
    // Необходим т.к. порт PostgreSQL контейнера ДИНАМИЧЕСКИЙ (random free port).
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);        // реальный JDBC URL
        registry.add("spring.datasource.username", postgres::getUsername);   // "cinema"
        registry.add("spring.datasource.password", postgres::getPassword);   // "cinema"
        // Явный стандартный PostgreSQL драйвер (не TC-драйвер из application-test.yml)
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    // @MockBean KafkaListenerContainerFactory — особенность notification-service!
    // Даже при исключении KafkaAutoConfiguration, Spring может попытаться создать
    //   KafkaListenerContainerFactory бин для @KafkaListener методов в NotificationConsumer.
    // @MockBean предоставляет заглушку и предотвращает ошибку при старте контекста.
    // Это отличие от order-service (там @MockBean KafkaTemplate было достаточно):
    //   в notification-service основные Kafka бины — это consumer factory и listener container factory.
    @MockBean
    KafkaListenerContainerFactory<?> kafkaListenerContainerFactory;

    // Реальный NotificationService — тестируем с реальной PostgreSQL через Testcontainers.
    @Autowired
    private NotificationService notificationService;

    // ================================================================
    // Тест: создание и получение уведомлений — изоляция по userId
    // ================================================================

    @Test
    void createAndGetNotifications_onlyReturnsNotificationsForRequestedUser() {
        // Arrange: создаём 3 уведомления: 2 для user 1L, 1 для user 2L
        notificationService.createNotification(1L, "Test Title", "Test Content");
        notificationService.createNotification(1L, "Another", "More");
        notificationService.createNotification(2L, "Other user", "X");

        // Act: запрашиваем уведомления для разных пользователей
        List<NotificationDto> user1Notifications = notificationService.getNotifications(1L);
        List<NotificationDto> user2Notifications = notificationService.getNotifications(2L);

        // Assert: каждый видит только свои уведомления
        assertThat(user1Notifications).hasSize(2);
        // allMatch — все элементы соответствуют условию: userId = 1L
        assertThat(user1Notifications).allMatch(n -> n.getUserId().equals(1L));

        assertThat(user2Notifications).hasSize(1);
        assertThat(user2Notifications.get(0).getTitle()).isEqualTo("Other user");
    }

    // ================================================================
    // Тест: полный цикл markAsRead
    // ================================================================

    @Test
    void markAsRead_flow_updatesReadFlag() {
        // Arrange: создаём уведомление, сохраняем его id
        Notification created = notificationService.createNotification(10L, "T", "C");
        Long id = created.getId();  // реальный id из PostgreSQL (SERIAL)

        // Verify: уведомление изначально непрочитано
        List<NotificationDto> before = notificationService.getNotifications(10L);
        // anyMatch — хотя бы один элемент: id совпадает И read=false
        assertThat(before).anyMatch(n -> n.getId().equals(id) && !n.isRead());

        // Act: отмечаем как прочитанное
        notificationService.markAsRead(id, 10L);

        // Assert: уведомление теперь прочитано в PostgreSQL
        List<NotificationDto> after = notificationService.getNotifications(10L);
        assertThat(after).anyMatch(n -> n.getId().equals(id) && n.isRead());
    }

    // ================================================================
    // Тест: чужой пользователь не может прочитать уведомление
    // ================================================================

    @Test
    void markAsRead_wrongUser_throwsSecurityException() {
        // Уведомление создано для userId=20
        Notification created = notificationService.createNotification(20L, "T", "C");
        Long id = created.getId();

        // userId=99 пытается отметить чужое уведомление
        // NotificationService проверяет: notification.getUserId()==userId → 20 ≠ 99 → SecurityException
        assertThatThrownBy(() -> notificationService.markAsRead(id, 99L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Access denied");
    }

    // ================================================================
    // Тест: уведомление не найдено
    // ================================================================

    @Test
    void markAsRead_notFound_throwsEntityNotFoundException() {
        // Long.MAX_VALUE — гарантированно несуществующий id в тестовой БД
        assertThatThrownBy(() -> notificationService.markAsRead(Long.MAX_VALUE, 1L))
                .isInstanceOf(EntityNotFoundException.class);
        // EntityNotFoundException — jakarta.persistence исключение (не кастомное).
    }
}
