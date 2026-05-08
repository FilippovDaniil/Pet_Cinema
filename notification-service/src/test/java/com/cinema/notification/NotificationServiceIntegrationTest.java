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

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@ActiveProfiles("test")
@Testcontainers
class NotificationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("notification_db_test")
                    .withUsername("cinema")
                    .withPassword("cinema");

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /**
     * Kafka auto-configuration is excluded via @SpringBootTest properties, but
     * if any bean still references KafkaListenerContainerFactory we provide a mock.
     */
    @MockBean
    KafkaListenerContainerFactory<?> kafkaListenerContainerFactory;

    @Autowired
    private NotificationService notificationService;

    // ------------------------------------------------------------------ //
    // createAndGet
    // ------------------------------------------------------------------ //

    @Test
    void createAndGetNotifications_onlyReturnsNotificationsForRequestedUser() {
        notificationService.createNotification(1L, "Test Title", "Test Content");
        notificationService.createNotification(1L, "Another", "More");
        notificationService.createNotification(2L, "Other user", "X");

        List<NotificationDto> user1Notifications = notificationService.getNotifications(1L);
        List<NotificationDto> user2Notifications = notificationService.getNotifications(2L);

        assertThat(user1Notifications).hasSize(2);
        assertThat(user1Notifications).allMatch(n -> n.getUserId().equals(1L));

        assertThat(user2Notifications).hasSize(1);
        assertThat(user2Notifications.get(0).getTitle()).isEqualTo("Other user");
    }

    // ------------------------------------------------------------------ //
    // markAsRead flow
    // ------------------------------------------------------------------ //

    @Test
    void markAsRead_flow_updatesReadFlag() {
        Notification created = notificationService.createNotification(10L, "T", "C");
        Long id = created.getId();

        // Initially not read
        List<NotificationDto> before = notificationService.getNotifications(10L);
        assertThat(before).anyMatch(n -> n.getId().equals(id) && !n.isRead());

        // Mark as read
        notificationService.markAsRead(id, 10L);

        // Now should be read
        List<NotificationDto> after = notificationService.getNotifications(10L);
        assertThat(after).anyMatch(n -> n.getId().equals(id) && n.isRead());
    }

    // ------------------------------------------------------------------ //
    // markAsRead wrong user
    // ------------------------------------------------------------------ //

    @Test
    void markAsRead_wrongUser_throwsSecurityException() {
        Notification created = notificationService.createNotification(20L, "T", "C");
        Long id = created.getId();

        assertThatThrownBy(() -> notificationService.markAsRead(id, 99L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Access denied");
    }

    // ------------------------------------------------------------------ //
    // markAsRead not found
    // ------------------------------------------------------------------ //

    @Test
    void markAsRead_notFound_throwsEntityNotFoundException() {
        assertThatThrownBy(() -> notificationService.markAsRead(Long.MAX_VALUE, 1L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
