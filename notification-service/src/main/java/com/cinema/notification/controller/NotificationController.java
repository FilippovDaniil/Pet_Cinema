package com.cinema.notification.controller;

import com.cinema.dto.notification.NotificationDto;
import com.cinema.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @Slf4j — Logger для логирования входящих запросов.
@Slf4j
// @RestController — @Controller + @ResponseBody: все методы возвращают JSON данные.
@RestController
// @RequestMapping("/api/notifications") — базовый путь.
// api-gateway маршрутизирует /api/notifications/** → lb://notification-service.
@RequestMapping("/api/notifications")
// @RequiredArgsConstructor — конструктор для final поля notificationService.
@RequiredArgsConstructor
public class NotificationController {

    // notificationService — бизнес-логика: создание, получение, отметка прочтения.
    private final NotificationService notificationService;

    // ================================================================
    // GET /api/notifications — получить уведомления текущего пользователя
    // ================================================================

    // @GetMapping без параметров — маппинг GET запроса на базовый путь /api/notifications.
    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(Authentication authentication) {
        // authentication.getPrincipal() — возвращает principal установленный в JwtAuthFilter.
        // JwtAuthFilter устанавливает: principal = userId (String) из JWT sub claim.
        // (String) кастинг — безопасен т.к. JwtAuthFilter всегда устанавливает String principal.
        // Long.parseLong("42") → 42L.
        // Отличие от SupportController: там authentication.getName() (эквивалентно getPrincipal().toString()).
        // Здесь явный кастинг — оба подхода работают одинаково.
        Long userId = Long.parseLong((String) authentication.getPrincipal());
        log.info("GET /api/notifications for userId={}", userId);
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    // ================================================================
    // PATCH /api/notifications/{id}/read — отметить уведомление как прочитанное
    // ================================================================

    // @PatchMapping("/{id}/read") — HTTP PATCH на /api/notifications/5/read.
    // PATCH используется для частичного обновления (только поле read меняется).
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            // @PathVariable Long id — извлекает {id} из URL (/notifications/5/read → id=5).
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = Long.parseLong((String) authentication.getPrincipal());
        log.info("PATCH /api/notifications/{}/read for userId={}", id, userId);
        // Сервис проверяет что уведомление принадлежит userId.
        // Если нет → SecurityException → GlobalExceptionHandler → HTTP 403.
        return ResponseEntity.ok(notificationService.markAsRead(id, userId));
    }
}
