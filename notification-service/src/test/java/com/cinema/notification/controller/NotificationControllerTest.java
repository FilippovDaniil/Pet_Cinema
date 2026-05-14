package com.cinema.notification.controller;

import com.cinema.dto.notification.NotificationDto;
import com.cinema.notification.security.JwtUtils;
import com.cinema.notification.security.SecurityConfig;
import com.cinema.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest(NotificationController.class) — загружает только веб-слой:
//   NotificationController + Spring Security фильтры + Jackson.
//   Репозитории, Kafka — НЕ загружаются.
@WebMvcTest(NotificationController.class)
// @Import(SecurityConfig.class) — обязательно для загрузки правил безопасности.
// SecurityConfig настраивает JwtAuthFilter в цепочке фильтров.
@Import(SecurityConfig.class)
class NotificationControllerTest {

    // MockMvc — имитирует HTTP запросы без реального Tomcat.
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper — для сериализации/десериализации JSON в тестах.
    // @Autowired (не вручную создаётся) — Spring Boot AutoConfiguration создаёт бин ObjectMapper.
    // В отличие от SupportControllerTest: здесь не нужен JavaTimeModule
    //   т.к. в запросах нет LocalDateTime в теле, а в ответах Jackson справляется с дефолтными настройками.
    @Autowired
    private ObjectMapper objectMapper;

    // @MockBean — заглушки для зависимостей контроллера.
    @MockBean
    private NotificationService notificationService;

    // @MockBean JwtUtils — ОБЯЗАТЕЛЕН: JwtAuthFilter зависит от JwtUtils через конструктор.
    // Без @MockBean JwtUtils: Spring не может создать JwtAuthFilter бин → тест падает.
    @MockBean
    private JwtUtils jwtUtils;

    // ================================================================
    // Вспомогательные методы
    // ================================================================

    // userAuth — создаёт UsernamePasswordAuthenticationToken для имитации аутентифицированного пользователя.
    // principal = userId (String) — как устанавливает JwtAuthFilter в продакшн.
    // NotificationController делает: (String) authentication.getPrincipal() → Long.parseLong(userId).
    // ROLE_USER — роль (в notification-service не проверяется, но нужна для is-authenticated).
    private UsernamePasswordAuthenticationToken userAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,   // principal = String userId ("1", "2" и т.д.)
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    // sampleDto — создаёт тестовый NotificationDto.
    private NotificationDto sampleDto(Long id, Long userId) {
        return NotificationDto.builder()
                .id(id)
                .userId(userId)
                .title("Test title")
                .content("Test content")
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ================================================================
    // GET /api/notifications
    // ================================================================

    @Test
    void getNotifications_authenticated_returns200WithList() throws Exception {
        // Arrange: у userId=1 есть 2 уведомления
        List<NotificationDto> dtos = List.of(sampleDto(1L, 1L), sampleDto(2L, 1L));
        when(notificationService.getNotifications(1L)).thenReturn(dtos);

        // Act + Assert: аутентифицированный запрос возвращает 200 с 2 уведомлениями
        mockMvc.perform(get("/api/notifications")
                        // authentication(userAuth("1")) — устанавливает principal="1", role="ROLE_USER"
                        .with(authentication(userAuth("1"))))
                .andExpect(status().isOk())                    // HTTP 200
                .andExpect(jsonPath("$.length()").value(2))    // 2 уведомления
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void getNotifications_unauthenticated_returns401Or403() throws Exception {
        // Без аутентификации — Spring Security вернёт 401 или 403.
        // is4xxClientError() — проверяет любой 4xx статус (401, 403).
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is4xxClientError());
    }

    // ================================================================
    // PATCH /api/notifications/{id}/read
    // ================================================================

    @Test
    void markAsRead_authenticated_returns200WithUpdatedDto() throws Exception {
        // Arrange: уведомление id=5 отмечается как прочитанное
        NotificationDto readDto = NotificationDto.builder()
                .id(5L)
                .userId(1L)
                .title("Test")
                .content("Content")
                .read(true)          // read=true после отметки
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationService.markAsRead(5L, 1L)).thenReturn(readDto);

        // PATCH /api/notifications/5/read — HTTP PATCH для частичного обновления
        mockMvc.perform(patch("/api/notifications/5/read")
                        .with(authentication(userAuth("1"))))
                .andExpect(status().isOk())                    // HTTP 200
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.read").value(true));    // read=true в ответе
    }

    @Test
    void markAsRead_notFound_returns404() throws Exception {
        // Уведомление 999 не существует → EntityNotFoundException → GlobalExceptionHandler → 404
        when(notificationService.markAsRead(anyLong(), anyLong()))
                .thenThrow(new EntityNotFoundException("Notification not found: 999"));

        mockMvc.perform(patch("/api/notifications/999/read")
                        .with(authentication(userAuth("1"))))
                .andExpect(status().isNotFound());  // HTTP 404
    }

    @Test
    void markAsRead_wrongUser_returns403() throws Exception {
        // userId=2 пытается прочитать уведомление которое принадлежит другому
        // NotificationService бросает SecurityException → GlobalExceptionHandler → 403
        when(notificationService.markAsRead(anyLong(), anyLong()))
                .thenThrow(new SecurityException("Access denied to notification id=5"));

        mockMvc.perform(patch("/api/notifications/5/read")
                        .with(authentication(userAuth("2"))))  // другой пользователь
                .andExpect(status().isForbidden());  // HTTP 403
    }
}
