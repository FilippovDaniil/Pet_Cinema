package com.cinema.notification.service;

import com.cinema.dto.notification.NotificationDto;
import com.cinema.notification.entity.Notification;
import com.cinema.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class) — JUnit 5 расширение Mockito.
// Инициализирует @Mock и @InjectMocks перед каждым тестом. Нет Spring Context — быстро.
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    // @Mock — заглушка репозитория, нет реальной БД.
    @Mock
    private NotificationRepository notificationRepository;

    // @InjectMocks — реальный NotificationService с инжектированным @Mock репозиторием.
    @InjectMocks
    private NotificationService notificationService;

    // ================================================================
    // createNotification тесты
    // ================================================================

    @Test
    void createNotification_success_savesAndReturnsEntity() {
        // Arrange: мок возвращает сохранённую сущность с id=1
        Notification saved = Notification.builder()
                .id(1L)
                .userId(1L)
                .title("Title")
                .content("Content")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        // Act
        Notification result = notificationService.createNotification(1L, "Title", "Content");

        // Assert: проверяем что в save() передана корректная сущность
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification captured = captor.getValue();

        assertThat(captured.getUserId()).isEqualTo(1L);     // userId правильный
        assertThat(captured.getTitle()).isEqualTo("Title"); // заголовок из параметра
        assertThat(captured.getContent()).isEqualTo("Content");
        assertThat(captured.isRead()).isFalse();             // новые уведомления непрочитаны

        // Проверяем возвращённую сущность
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);  // id назначен при сохранении
    }

    // ================================================================
    // getNotifications тесты
    // ================================================================

    @Test
    void getNotifications_returnsUserNotifications() {
        // Arrange: у userId=1 есть 3 уведомления (2 непрочитанных, 1 прочитанное)
        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = List.of(
                buildNotification(1L, 1L, "T1", "C1", false, now),
                buildNotification(2L, 1L, "T2", "C2", false, now),
                buildNotification(3L, 1L, "T3", "C3", true,  now)  // прочитанное
        );

        when(notificationRepository.findByUserId(1L)).thenReturn(notifications);

        // Act
        List<NotificationDto> result = notificationService.getNotifications(1L);

        // Assert: 3 DTO правильно смаппированы из сущностей
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getTitle()).isEqualTo("T1");
        assertThat(result.get(1).getTitle()).isEqualTo("T2");
        assertThat(result.get(2).isRead()).isTrue();  // третье прочитано
    }

    @Test
    void getNotifications_emptyList_returnsEmpty() {
        // Пользователь 99 не имеет уведомлений
        when(notificationRepository.findByUserId(99L)).thenReturn(Collections.emptyList());

        List<NotificationDto> result = notificationService.getNotifications(99L);

        assertThat(result).isEmpty();  // не null, а пустой список
    }

    // ================================================================
    // markAsRead тесты
    // ================================================================

    @Test
    void markAsRead_success_setsReadTrueAndSaves() {
        // Arrange: непрочитанное уведомление принадлежит userId=1
        Notification notification = buildNotification(10L, 1L, "T", "C", false, LocalDateTime.now());
        // Мок save() возвращает обновлённую сущность с read=true
        Notification saved = buildNotification(10L, 1L, "T", "C", true, LocalDateTime.now());

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(saved);

        // Act
        NotificationDto result = notificationService.markAsRead(10L, 1L);

        // Assert: read установлен в true на ОРИГИНАЛЬНОМ объекте (не возвращаемом из save)
        // notification — "attached" entity: setRead(true) меняет поле на месте.
        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);  // save() вызван с изменённым объектом
        assertThat(result.isRead()).isTrue();                // DTO тоже прочитан
    }

    @Test
    void markAsRead_wrongUser_throwsSecurityException() {
        // Уведомление принадлежит userId=2, но запрашивает userId=1
        Notification notification = buildNotification(10L, 2L, "T", "C", false, LocalDateTime.now());

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        // Assert: SecurityException если userId не совпадает с notification.getUserId()
        assertThatThrownBy(() -> notificationService.markAsRead(10L, 1L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Access denied");

        // save() НЕ вызван (прерывается до отметки прочитанным)
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_notFound_throwsEntityNotFoundException() {
        // Уведомление с id=999 не существует
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(999L, 1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");  // "Notification not found: 999"
    }

    // ================================================================
    // Вспомогательный метод
    // ================================================================

    // buildNotification — создаёт тестовую Notification сущность.
    // Принимает все поля включая read и createdAt — для гибкости в тестах.
    private Notification buildNotification(Long id, Long userId, String title, String content,
                                           boolean read, LocalDateTime createdAt) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .title(title)
                .content(content)
                .read(read)
                .createdAt(createdAt)
                .build();
    }
}
