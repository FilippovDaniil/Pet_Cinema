package com.cinema.support.controller;

import com.cinema.dto.support.*;
import com.cinema.support.service.SupportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SupportController.class)
@Import(com.cinema.support.config.SecurityConfig.class)
class SupportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupportService supportService;

    @MockBean
    private com.cinema.support.security.JwtUtils jwtUtils;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // Helper: build a UsernamePasswordAuthenticationToken acting as a named principal
    private static RequestPostProcessor authenticatedAs(
            String name, String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return authentication(new UsernamePasswordAuthenticationToken(name, null, grantedAuthorities));
    }

    private SupportTicketDto buildTicketDto(Long id, Long clientId) {
        return SupportTicketDto.builder()
                .id(id).clientId(clientId).subject("Help needed")
                .status("OPEN").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private SupportMessageDto buildMessageDto(Long id, Long ticketId) {
        return SupportMessageDto.builder()
                .id(id).ticketId(ticketId).senderId(1L)
                .content("Hello support").sentAt(LocalDateTime.now())
                .build();
    }

    // ------------------------------------------------------------------ POST /api/support/tickets

    @Test
    void createTicket_authenticatedClient_returns201() throws Exception {
        SupportTicketCreateRequest request = SupportTicketCreateRequest.builder()
                .subject("I need help").build();
        SupportTicketDto response = buildTicketDto(1L, 42L);

        when(supportService.createTicket(any(SupportTicketCreateRequest.class), eq(42L)))
                .thenReturn(response);

        mockMvc.perform(post("/api/support/tickets")
                        .with(csrf())
                        .with(authenticatedAs("42", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.clientId").value(42))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createTicket_unauthenticated_returns401Or403() throws Exception {
        SupportTicketCreateRequest request = SupportTicketCreateRequest.builder()
                .subject("No auth").build();

        mockMvc.perform(post("/api/support/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403);
                });
    }

    // ------------------------------------------------------------------ GET /api/support/tickets/my

    @Test
    void getMyTickets_authenticatedClient_returns200WithList() throws Exception {
        SupportTicketDto t1 = buildTicketDto(1L, 7L);
        SupportTicketDto t2 = buildTicketDto(2L, 7L);

        when(supportService.getMyTickets(7L)).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/support/tickets/my")
                        .with(authenticatedAs("7", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].clientId").value(7));
    }

    // ------------------------------------------------------------------ GET /api/support/tickets

    @Test
    void getAllTickets_adminRole_returns200WithAllTickets() throws Exception {
        SupportTicketDto t1 = buildTicketDto(1L, 1L);
        SupportTicketDto t2 = buildTicketDto(2L, 2L);
        SupportTicketDto t3 = buildTicketDto(3L, 3L);

        when(supportService.getAllTickets()).thenReturn(List.of(t1, t2, t3));

        mockMvc.perform(get("/api/support/tickets")
                        .with(authenticatedAs("99", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getAllTickets_clientRole_returns403() throws Exception {
        mockMvc.perform(get("/api/support/tickets")
                        .with(authenticatedAs("7", "CLIENT")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ POST /api/support/tickets/{id}/messages

    @Test
    void sendMessage_authenticatedClient_returns201WithMessage() throws Exception {
        SupportMessageRequest request = SupportMessageRequest.builder()
                .content("Please help me!").build();
        SupportMessageDto response = buildMessageDto(10L, 1L);

        when(supportService.sendMessage(eq(1L), any(SupportMessageRequest.class), eq(42L), eq("CLIENT")))
                .thenReturn(response);

        mockMvc.perform(post("/api/support/tickets/1/messages")
                        .with(csrf())
                        .with(authenticatedAs("42", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ticketId").value(1))
                .andExpect(jsonPath("$.content").value("Hello support"));
    }

    // ------------------------------------------------------------------ GET /api/support/tickets/{id}/messages

    @Test
    void getMessages_authenticatedClient_returns200WithMessageList() throws Exception {
        SupportMessageDto msg1 = buildMessageDto(1L, 5L);
        SupportMessageDto msg2 = buildMessageDto(2L, 5L);

        when(supportService.getMessages(eq(5L), eq(7L), eq("CLIENT")))
                .thenReturn(List.of(msg1, msg2));

        mockMvc.perform(get("/api/support/tickets/5/messages")
                        .with(authenticatedAs("7", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticketId").value(5));
    }

    // ------------------------------------------------------------------ PUT /api/support/tickets/{id}/assign

    @Test
    void assignAdmin_adminRole_returns200() throws Exception {
        AssignAdminRequest request = AssignAdminRequest.builder().adminId(88L).build();
        SupportTicketDto response = buildTicketDto(1L, 5L);
        response.setAdminId(88L);

        when(supportService.assignAdmin(eq(1L), eq(88L))).thenReturn(response);

        mockMvc.perform(put("/api/support/tickets/1/assign")
                        .with(csrf())
                        .with(authenticatedAs("99", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ------------------------------------------------------------------ PATCH /api/support/tickets/{id}/close

    @Test
    void closeTicket_adminRole_returns200() throws Exception {
        SupportTicketDto response = buildTicketDto(1L, 5L);
        response.setStatus("CLOSED");

        when(supportService.closeTicket(1L)).thenReturn(response);

        mockMvc.perform(patch("/api/support/tickets/1/close")
                        .with(csrf())
                        .with(authenticatedAs("99", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }
}
