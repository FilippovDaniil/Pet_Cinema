package com.cinema.hall.controller;

import com.cinema.dto.hall.SessionCreateRequest;
import com.cinema.dto.hall.SessionDto;
import com.cinema.hall.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Тесты веб-слоя SessionController.
// @WebMvcTest загружает только контроллер + фильтры Spring Security + конвертеры.
// Бизнес-логика (SessionService) замокирована.
@WebMvcTest(SessionController.class)
@Import(com.cinema.hall.config.SecurityConfig.class) // Загружаем SecurityConfig с JwtAuthFilter
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionService sessionService;

    // @MockBean JwtUtils — необходим потому что JwtAuthFilter (из SecurityConfig)
    // имеет зависимость на JwtUtils через @RequiredArgsConstructor.
    // Без этого Spring Context не запустится.
    @MockBean
    private com.cinema.hall.security.JwtUtils jwtUtils;

    // Вспомогательный метод для создания тестового SessionDto.
    private SessionDto buildSessionDto(Long id) {
        return SessionDto.builder()
                .id(id)
                .movieId(1L)
                .hallId(2L)
                .startTime(LocalDateTime.of(2026, 6, 1, 14, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 16, 0))
                .basePrice(new BigDecimal("12.00"))
                .active(true)
                .build();
    }

    // ------------------------------------------------------------------ GET /api/sessions

    @Test
    void getSessions_publicEndpoint_returns200() throws Exception {
        SessionDto s1 = buildSessionDto(1L);
        SessionDto s2 = buildSessionDto(2L);

        // isNull() — матчер Mockito для null-аргумента (аналог eq(null), но более явный).
        // Все параметры null → сервис вызывается с getSessions(null, null, null, null).
        when(sessionService.getSessions(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void getSessions_withMovieIdParam_returns200() throws Exception {
        SessionDto s = buildSessionDto(3L);

        // eq(5L) — только первый параметр (movieId) задан, остальные null.
        // isNull() — явно указываем что параметры должны быть null.
        when(sessionService.getSessions(eq(5L), isNull(), isNull(), isNull()))
                .thenReturn(List.of(s));

        // ?movieId=5 → Spring конвертирует "5" в Long.valueOf(5L) для @RequestParam Long movieId
        mockMvc.perform(get("/api/sessions").param("movieId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3));
    }

    // ------------------------------------------------------------------ POST /api/sessions

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN") // Аутентифицированный ADMIN
    void createSession_withAdminRole_returns201() throws Exception {
        SessionCreateRequest request = SessionCreateRequest.builder()
                .movieId(1L)
                .hallId(2L)
                .startTime(LocalDateTime.of(2026, 7, 1, 10, 0))
                .endTime(LocalDateTime.of(2026, 7, 1, 12, 0))
                .basePrice(new BigDecimal("15.00"))
                .build();

        SessionDto response = buildSessionDto(10L);

        when(sessionService.createSession(any(SessionCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/sessions")
                        .with(csrf())                          // Нужен для POST в Spring Security тестах
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())               // HTTP 201
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.active").value(true));
    }

    // ------------------------------------------------------------------ DELETE /api/sessions/{id}

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void deleteSession_withAdminRole_returns204() throws Exception {
        // doNothing() — мокируем void-метод
        doNothing().when(sessionService).deleteSession(5L);

        mockMvc.perform(delete("/api/sessions/5").with(csrf()))
                .andExpect(status().isNoContent()); // HTTP 204 No Content
        // Примечание: deleteSession — мягкое удаление (active=false), не физическое.
        // С точки зрения HTTP интерфейса это не важно — всегда 204.
    }
}
