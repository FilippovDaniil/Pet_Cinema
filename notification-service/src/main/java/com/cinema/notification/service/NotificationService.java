package com.cinema.notification.service;

import com.cinema.dto.notification.NotificationDto;
import com.cinema.notification.entity.Notification;
import com.cinema.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

// @Slf4j — Lombok: Logger для info/warn логирования.
@Slf4j
// @Service — маркер слоя бизнес-логики.
@Service
// @RequiredArgsConstructor — конструктор для final поля notificationRepository.
@RequiredArgsConstructor
public class NotificationService {

    // notificationRepository — JPA репозиторий для таблицы notifications.
    private final NotificationRepository notificationRepository;

    // ================================================================
    // createNotification — создать уведомление в БД
    // ================================================================

    // @Transactional — операция в одной транзакции PostgreSQL.
    // Вызывается из NotificationConsumer (Kafka listener поток) — асинхронно от HTTP запросов.
    @Transactional
    public Notification createNotification(Long userId, String title, String content) {
        // Создаём сущность Notification через Builder.
        // @CreationTimestamp в сущности автоматически установит createdAt при INSERT.
        // read = false — новые уведомления всегда непрочитанные.
        Notification notification = Notification.builder()
                .userId(userId)    // получатель уведомления
                .title(title)      // заголовок ("Билет куплен!", "Новое сообщение в поддержке")
                .content(content)  // текст (детали события)
                .read(false)       // непрочитанное
                .build();

        // Сохраняем в PostgreSQL. После save() notification.getId() заполнен.
        Notification saved = notificationRepository.save(notification);
        log.info("Created notification id={} for userId={}", saved.getId(), userId);

        // Возвращаем сущность (не DTO) — NotificationConsumer не использует возвращаемое значение,
        // но IntegrationTest использует returned Notification для получения id.
        return saved;
    }

    // ================================================================
    // getNotifications — получить все уведомления пользователя
    // ================================================================

    // readOnly = true — оптимизация для SELECT: Hibernate не отслеживает изменения объектов.
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(Long userId) {
        // findByUserId → SELECT * FROM notifications WHERE user_id = ?
        // Преобразуем каждую сущность в DTO перед возвратом клиенту.
        return notificationRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // ================================================================
    // markAsRead — отметить уведомление как прочитанное
    // ================================================================

    @Transactional
    public NotificationDto markAsRead(Long id, Long userId) {
        // Ищем уведомление по id.
        // EntityNotFoundException (из jakarta.persistence) — если не найдено.
        // Это отличие от других сервисов (ResourceNotFoundException) —
        //   notification-service использует стандартное JPA исключение вместо кастомного.
        // GlobalExceptionHandler перехватывает EntityNotFoundException → HTTP 404.
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + id));

        // Проверка владельца: только сам пользователь может отметить своё уведомление.
        // Важно: без этой проверки любой аутентифицированный пользователь мог бы изменить чужое уведомление.
        // SecurityException — стандартное Java исключение (java.lang.SecurityException).
        // GlobalExceptionHandler перехватывает SecurityException → HTTP 403.
        if (!notification.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to notification id=" + id);
        }

        // Устанавливаем read = true.
        // notification — "attached" сущность в текущей транзакции (Hibernate отслеживает изменения).
        // При завершении транзакции Hibernate автоматически выполнит UPDATE.
        // Но мы явно вызываем save() для возврата обновлённой сущности.
        notification.setRead(true);

        // save() → UPDATE notifications SET read = true WHERE id = ?
        return toDto(notificationRepository.save(notification));
    }

    // ================================================================
    // toDto — маппинг сущности в DTO
    // ================================================================

    // toDto — конвертирует Notification (JPA сущность) в NotificationDto (ответ API).
    // Принимает сокращённое имя параметра 'n' (принятая конвенция в маппинг методах).
    private NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .title(n.getTitle())
                .content(n.getContent())
                .read(n.isRead())          // isRead() — стандартный геттер для boolean (не getRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
