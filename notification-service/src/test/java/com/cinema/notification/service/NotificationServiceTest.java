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

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    // ------------------------------------------------------------------ //
    // createNotification
    // ------------------------------------------------------------------ //

    @Test
    void createNotification_success_savesAndReturnsEntity() {
        Notification saved = Notification.builder()
                .id(1L)
                .userId(1L)
                .title("Title")
                .content("Content")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        Notification result = notificationService.createNotification(1L, "Title", "Content");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification captured = captor.getValue();

        assertThat(captured.getUserId()).isEqualTo(1L);
        assertThat(captured.getTitle()).isEqualTo("Title");
        assertThat(captured.getContent()).isEqualTo("Content");
        assertThat(captured.isRead()).isFalse();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    // ------------------------------------------------------------------ //
    // getNotifications
    // ------------------------------------------------------------------ //

    @Test
    void getNotifications_returnsUserNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = List.of(
                buildNotification(1L, 1L, "T1", "C1", false, now),
                buildNotification(2L, 1L, "T2", "C2", false, now),
                buildNotification(3L, 1L, "T3", "C3", true,  now)
        );

        when(notificationRepository.findByUserId(1L)).thenReturn(notifications);

        List<NotificationDto> result = notificationService.getNotifications(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getUserId()).isEqualTo(1L);
        assertThat(result.get(0).getTitle()).isEqualTo("T1");
        assertThat(result.get(1).getTitle()).isEqualTo("T2");
        assertThat(result.get(2).isRead()).isTrue();
    }

    @Test
    void getNotifications_emptyList_returnsEmpty() {
        when(notificationRepository.findByUserId(99L)).thenReturn(Collections.emptyList());

        List<NotificationDto> result = notificationService.getNotifications(99L);

        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------ //
    // markAsRead
    // ------------------------------------------------------------------ //

    @Test
    void markAsRead_success_setsReadTrueAndSaves() {
        Notification notification = buildNotification(10L, 1L, "T", "C", false, LocalDateTime.now());
        Notification saved = buildNotification(10L, 1L, "T", "C", true, LocalDateTime.now());

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(saved);

        NotificationDto result = notificationService.markAsRead(10L, 1L);

        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
        assertThat(result.isRead()).isTrue();
    }

    @Test
    void markAsRead_wrongUser_throwsSecurityException() {
        Notification notification = buildNotification(10L, 2L, "T", "C", false, LocalDateTime.now());

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(10L, 1L))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Access denied");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_notFound_throwsEntityNotFoundException() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(999L, 1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ------------------------------------------------------------------ //
    // helpers
    // ------------------------------------------------------------------ //

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
