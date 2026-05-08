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

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtUtils jwtUtils;

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    /**
     * Builds an Authentication whose principal is the userId String — exactly
     * what JwtAuthFilter puts into the SecurityContext in production.
     */
    private UsernamePasswordAuthenticationToken userAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

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

    // ------------------------------------------------------------------ //
    // GET /api/notifications
    // ------------------------------------------------------------------ //

    @Test
    void getNotifications_authenticated_returns200WithList() throws Exception {
        List<NotificationDto> dtos = List.of(sampleDto(1L, 1L), sampleDto(2L, 1L));
        when(notificationService.getNotifications(1L)).thenReturn(dtos);

        mockMvc.perform(get("/api/notifications")
                        .with(authentication(userAuth("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    void getNotifications_unauthenticated_returns401Or403() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ //
    // PATCH /api/notifications/{id}/read
    // ------------------------------------------------------------------ //

    @Test
    void markAsRead_authenticated_returns200WithUpdatedDto() throws Exception {
        NotificationDto readDto = NotificationDto.builder()
                .id(5L)
                .userId(1L)
                .title("Test")
                .content("Content")
                .read(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationService.markAsRead(5L, 1L)).thenReturn(readDto);

        mockMvc.perform(patch("/api/notifications/5/read")
                        .with(authentication(userAuth("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void markAsRead_notFound_returns404() throws Exception {
        when(notificationService.markAsRead(anyLong(), anyLong()))
                .thenThrow(new EntityNotFoundException("Notification not found: 999"));

        mockMvc.perform(patch("/api/notifications/999/read")
                        .with(authentication(userAuth("1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAsRead_wrongUser_returns403() throws Exception {
        when(notificationService.markAsRead(anyLong(), anyLong()))
                .thenThrow(new SecurityException("Access denied to notification id=5"));

        mockMvc.perform(patch("/api/notifications/5/read")
                        .with(authentication(userAuth("2"))))
                .andExpect(status().isForbidden());
    }
}
