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

@WebMvcTest(SessionController.class)
@Import(com.cinema.hall.config.SecurityConfig.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private com.cinema.hall.security.JwtUtils jwtUtils;

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

        when(sessionService.getSessions(eq(5L), isNull(), isNull(), isNull()))
                .thenReturn(List.of(s));

        mockMvc.perform(get("/api/sessions").param("movieId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3));
    }

    // ------------------------------------------------------------------ POST /api/sessions

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.active").value(true));
    }

    // ------------------------------------------------------------------ DELETE /api/sessions/{id}

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void deleteSession_withAdminRole_returns204() throws Exception {
        doNothing().when(sessionService).deleteSession(5L);

        mockMvc.perform(delete("/api/sessions/5").with(csrf()))
                .andExpect(status().isNoContent());
    }
}
