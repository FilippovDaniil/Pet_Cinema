package com.cinema.notification.controller;

import com.cinema.dto.notification.NotificationDto;
import com.cinema.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getNotifications(Authentication authentication) {
        Long userId = Long.parseLong((String) authentication.getPrincipal());
        log.info("GET /api/notifications for userId={}", userId);
        return ResponseEntity.ok(notificationService.getNotifications(userId));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDto> markAsRead(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = Long.parseLong((String) authentication.getPrincipal());
        log.info("PATCH /api/notifications/{}/read for userId={}", id, userId);
        return ResponseEntity.ok(notificationService.markAsRead(id, userId));
    }
}
