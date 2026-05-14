package com.cinema.notification.repository;

import com.cinema.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// @Repository — маркер слоя данных. Spring Data JPA создаёт реализацию автоматически.
@Repository
// JpaRepository<Notification, Long>:
//   Notification — управляемая сущность
//   Long — тип первичного ключа
// Наследуем: save(), findById(), findAll(), deleteAll(), count() и другие CRUD методы.
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // findByUserId — получить все уведомления конкретного пользователя.
    // Spring Data генерирует: SELECT * FROM notifications WHERE user_id = ?
    // Используется в NotificationService.getNotifications(userId) и markAsRead().
    // Возвращает в порядке вставки (по id ASC по умолчанию).
    List<Notification> findByUserId(Long userId);

    // findByUserIdAndRead — получить уведомления пользователя по статусу прочтения.
    // Spring Data генерирует: SELECT * FROM notifications WHERE user_id = ? AND read = ?
    // Примеры вызовов:
    //   findByUserIdAndRead(1L, false) — непрочитанные уведомления пользователя 1
    //   findByUserIdAndRead(1L, true)  — прочитанные уведомления пользователя 1
    // Не используется напрямую в текущем коде, но доступен для будущего "badge" с числом непрочитанных.
    List<Notification> findByUserIdAndRead(Long userId, boolean read);
}
