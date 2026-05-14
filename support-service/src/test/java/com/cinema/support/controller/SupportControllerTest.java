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

// @WebMvcTest(SupportController.class) — загружает ТОЛЬКО веб слой:
//   SupportController + Spring MVC (DispatcherServlet) + Security фильтры + Jackson.
//   НЕ загружает: сервисы, репозитории, JPA, Kafka — они мокируются @MockBean.
//   Быстрый тест (секунды, не минуты как @SpringBootTest).
@WebMvcTest(SupportController.class)
// @Import(SecurityConfig.class) — ОБЯЗАТЕЛЬНО: @WebMvcTest не сканирует @Configuration классы.
// Без этого: SecurityConfig не загружается → SecurityFilterChain не настраивается →
//   @PreAuthorize("hasAuthority('ADMIN')") не работает → тесты 403/401 не проходят.
// JwtAuthFilter загружается автоматически как @Component (Spring MVC тест сканирует @Component).
@Import(com.cinema.support.config.SecurityConfig.class)
class SupportControllerTest {

    // MockMvc — Spring MVC Test framework: имитирует HTTP запросы без реального Tomcat.
    // Работает в памяти: DispatcherServlet обрабатывает запросы напрямую.
    @Autowired
    private MockMvc mockMvc;

    // @MockBean — Mockito заглушка вместо реального SupportService.
    // Spring регистрирует её в ApplicationContext → JwtAuthFilter её не видит, только контроллер.
    @MockBean
    private SupportService supportService;

    // @MockBean JwtUtils — КРИТИЧЕСКИ ВАЖЕН для @WebMvcTest.
    // JwtAuthFilter (загруженный как @Component) зависит от JwtUtils.
    // Без @MockBean JwtUtils Spring не может создать JwtAuthFilter бин → тест падает при запуске.
    // JwtAuthFilter НЕ мокируем сам — он должен вызываться для обработки Authentication.
    @MockBean
    private com.cinema.support.security.JwtUtils jwtUtils;

    // ObjectMapper создаётся вручную в @BeforeEach (не через @Autowired).
    // Причина: нужен JavaTimeModule для сериализации LocalDateTime в JSON.
    // Без JavaTimeModule: LocalDateTime сериализуется как массив чисел [2024, 1, 15, 10, 30, 0]
    //   вместо строки "2024-01-15T10:30:00" — и тесты падают.
    private ObjectMapper objectMapper;

    // @BeforeEach — выполняется ПЕРЕД каждым тестом.
    // Создаём новый ObjectMapper с JavaTimeModule для каждого теста.
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // JavaTimeModule — Jackson модуль для правильной сериализации java.time классов:
        //   LocalDateTime, LocalDate, Instant → JSON строки в ISO формате.
        objectMapper.registerModule(new JavaTimeModule());
    }

    // ================================================================
    // Вспомогательные методы
    // ================================================================

    // authenticatedAs — создаёт RequestPostProcessor для установки Authentication в запросе.
    // Используется с .with(authenticatedAs("42", "CLIENT")) вместо:
    //   1. @WithMockUser — устанавливает principal="user" (String), контроллер не может Long.parseLong("user")
    //   2. jwt() — требует spring-security-oauth2-resource-server в classpath (его нет)
    // UsernamePasswordAuthenticationToken:
    //   principal = name (String userId "42") → authentication.getName() → Long.parseLong("42") = 42L
    //   credentials = null (токен уже проверен)
    //   authorities = List.of(new SimpleGrantedAuthority("CLIENT"))
    private static RequestPostProcessor authenticatedAs(
            String name, String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return authentication(new UsernamePasswordAuthenticationToken(name, null, grantedAuthorities));
    }

    // buildTicketDto — создаёт тестовый SupportTicketDto для настройки моков
    private SupportTicketDto buildTicketDto(Long id, Long clientId) {
        return SupportTicketDto.builder()
                .id(id).clientId(clientId).subject("Help needed")
                .status("OPEN").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    // buildMessageDto — создаёт тестовый SupportMessageDto для настройки моков
    private SupportMessageDto buildMessageDto(Long id, Long ticketId) {
        return SupportMessageDto.builder()
                .id(id).ticketId(ticketId).senderId(1L)
                .content("Hello support").sentAt(LocalDateTime.now())
                .build();
    }

    // ================================================================
    // POST /api/support/tickets
    // ================================================================

    @Test
    void createTicket_authenticatedClient_returns201() throws Exception {
        SupportTicketCreateRequest request = SupportTicketCreateRequest.builder()
                .subject("I need help").build();
        SupportTicketDto response = buildTicketDto(1L, 42L);

        // Мок: сервис вернёт response когда будет вызван createTicket(любой запрос, 42L)
        when(supportService.createTicket(any(SupportTicketCreateRequest.class), eq(42L)))
                .thenReturn(response);

        mockMvc.perform(post("/api/support/tickets")
                        // csrf() — токен CSRF защиты. В реальном коде CSRF отключён (disable()),
                        // но Spring Security Test требует csrf() для POST запросов по умолчанию.
                        // Добавляем для совместимости с тестовым фреймворком.
                        .with(csrf())
                        // authenticatedAs("42", "CLIENT") — устанавливает principal="42", role="CLIENT"
                        .with(authenticatedAs("42", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())                   // HTTP 201
                .andExpect(jsonPath("$.id").value(1))             // id тикета
                .andExpect(jsonPath("$.clientId").value(42))      // clientId из JWT
                .andExpect(jsonPath("$.status").value("OPEN"));   // начальный статус
    }

    @Test
    void createTicket_unauthenticated_returns401Or403() throws Exception {
        SupportTicketCreateRequest request = SupportTicketCreateRequest.builder()
                .subject("No auth").build();

        // Запрос без аутентификации
        mockMvc.perform(post("/api/support/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // В support-service НЕТ HttpStatusEntryPoint → Spring Security 6 по умолчанию
                // возвращает 403 (не 401) для неаутентифицированных запросов.
                // В order-service есть HttpStatusEntryPoint(UNAUTHORIZED) → всегда 401.
                // Тест проверяет что статус 401 ИЛИ 403 (любой из них корректен).
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isIn(401, 403);
                });
    }

    // ================================================================
    // GET /api/support/tickets/my
    // ================================================================

    @Test
    void getMyTickets_authenticatedClient_returns200WithList() throws Exception {
        SupportTicketDto t1 = buildTicketDto(1L, 7L);
        SupportTicketDto t2 = buildTicketDto(2L, 7L);

        when(supportService.getMyTickets(7L)).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/support/tickets/my")
                        .with(authenticatedAs("7", "CLIENT")))  // principal="7" → clientId=7
                .andExpect(status().isOk())                          // HTTP 200
                .andExpect(jsonPath("$.length()").value(2))          // 2 тикета
                .andExpect(jsonPath("$[0].clientId").value(7));      // принадлежат клиенту 7
    }

    // ================================================================
    // GET /api/support/tickets (ADMIN only)
    // ================================================================

    @Test
    void getAllTickets_adminRole_returns200WithAllTickets() throws Exception {
        SupportTicketDto t1 = buildTicketDto(1L, 1L);
        SupportTicketDto t2 = buildTicketDto(2L, 2L);
        SupportTicketDto t3 = buildTicketDto(3L, 3L);

        when(supportService.getAllTickets()).thenReturn(List.of(t1, t2, t3));

        // authenticatedAs("99", "ADMIN") — роль "ADMIN" → @PreAuthorize("hasAuthority('ADMIN')") проходит
        mockMvc.perform(get("/api/support/tickets")
                        .with(authenticatedAs("99", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getAllTickets_clientRole_returns403() throws Exception {
        // CLIENT не имеет авторитета "ADMIN" → @PreAuthorize отказывает → 403 Forbidden
        mockMvc.perform(get("/api/support/tickets")
                        .with(authenticatedAs("7", "CLIENT")))
                .andExpect(status().isForbidden());  // HTTP 403
    }

    // ================================================================
    // POST /api/support/tickets/{id}/messages
    // ================================================================

    @Test
    void sendMessage_authenticatedClient_returns201WithMessage() throws Exception {
        SupportMessageRequest request = SupportMessageRequest.builder()
                .content("Please help me!").build();
        SupportMessageDto response = buildMessageDto(10L, 1L);

        // eq(1L) — проверяем ticketId из URL path variable
        // eq(42L) — проверяем senderId из authentication.getName() → Long.parseLong("42")
        // eq("CLIENT") — проверяем role из authorities.getAuthority()
        when(supportService.sendMessage(eq(1L), any(SupportMessageRequest.class), eq(42L), eq("CLIENT")))
                .thenReturn(response);

        mockMvc.perform(post("/api/support/tickets/1/messages")
                        .with(csrf())
                        .with(authenticatedAs("42", "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())                        // HTTP 201
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ticketId").value(1))
                .andExpect(jsonPath("$.content").value("Hello support")); // из buildMessageDto
    }

    // ================================================================
    // GET /api/support/tickets/{id}/messages
    // ================================================================

    @Test
    void getMessages_authenticatedClient_returns200WithMessageList() throws Exception {
        SupportMessageDto msg1 = buildMessageDto(1L, 5L);
        SupportMessageDto msg2 = buildMessageDto(2L, 5L);

        // eq(5L) — ticketId из URL, eq(7L) — userId из authentication, eq("CLIENT") — role
        when(supportService.getMessages(eq(5L), eq(7L), eq("CLIENT")))
                .thenReturn(List.of(msg1, msg2));

        mockMvc.perform(get("/api/support/tickets/5/messages")
                        .with(authenticatedAs("7", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ticketId").value(5));
    }

    // ================================================================
    // PUT /api/support/tickets/{id}/assign (ADMIN only)
    // ================================================================

    @Test
    void assignAdmin_adminRole_returns200() throws Exception {
        AssignAdminRequest request = AssignAdminRequest.builder().adminId(88L).build();
        SupportTicketDto response = buildTicketDto(1L, 5L);
        response.setAdminId(88L);  // после назначения adminId присутствует в DTO

        when(supportService.assignAdmin(eq(1L), eq(88L))).thenReturn(response);

        mockMvc.perform(put("/api/support/tickets/1/assign")
                        .with(csrf())
                        .with(authenticatedAs("99", "ADMIN"))  // только ADMIN может назначать
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ================================================================
    // PATCH /api/support/tickets/{id}/close (ADMIN only)
    // ================================================================

    @Test
    void closeTicket_adminRole_returns200() throws Exception {
        SupportTicketDto response = buildTicketDto(1L, 5L);
        response.setStatus("CLOSED");  // статус изменён

        when(supportService.closeTicket(1L)).thenReturn(response);

        mockMvc.perform(patch("/api/support/tickets/1/close")  // HTTP PATCH
                        .with(csrf())
                        .with(authenticatedAs("99", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }
}
