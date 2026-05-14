package com.cinema.order.controller;

import com.cinema.dto.order.FoodOrderRequest;
import com.cinema.dto.order.OrderDto;
import com.cinema.dto.order.PaymentWebhookRequest;
import com.cinema.dto.order.SellerTicketOrderRequest;
import com.cinema.dto.order.TicketOrderRequest;
import com.cinema.order.config.SecurityConfig;
import com.cinema.order.security.JwtUtils;
import com.cinema.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest — загружает только веб-слой: OrderController + Spring Security фильтры + Jackson.
// Не загружает: сервисы, репозитории, JPA — они мокируются через @MockBean.
@WebMvcTest(OrderController.class)
// @Import(SecurityConfig.class) — ОБЯЗАТЕЛЬНО: @WebMvcTest не сканирует @Configuration классы.
// Без этого SecurityConfig не загружается → SecurityFilterChain не настраивается → 401/403 не работают.
// JwtAuthFilter загружается через SecurityConfig (он зарегистрирован через addFilterBefore).
@Import(SecurityConfig.class)
class OrderControllerTest {

    // MockMvc — имитирует HTTP запросы без запуска реального сервера (Tomcat)
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper используется для сериализации request body в JSON строку
    @Autowired
    private ObjectMapper objectMapper;

    // @MockBean — регистрирует Mockito-заглушку вместо реального Spring бина в контексте теста
    @MockBean
    private OrderService orderService;

    // JwtUtils @MockBean — ОБЯЗАТЕЛЕН для @WebMvcTest:
    // JwtAuthFilter (загруженный через SecurityConfig) зависит от JwtUtils через @RequiredArgsConstructor.
    // Без этого @MockBean Spring не может создать JwtAuthFilter бин → тест падает при запуске.
    // JwtAuthFilter НЕ мокируем — иначе он поглощает все запросы без аутентификации.
    @MockBean
    private JwtUtils jwtUtils;

    // Вспомогательный метод: создаёт тестовый OrderDto
    private OrderDto buildOrderDto(Long id, Long userId, Long sellerId,
                                   String type, String status, BigDecimal totalPrice) {
        return OrderDto.builder()
                .id(id).userId(userId).sellerId(sellerId)
                .orderType(type).status(status).totalPrice(totalPrice)
                .items(new ArrayList<>()).createdAt(LocalDateTime.now())
                .build();
    }

    // Вспомогательный метод: создаёт UsernamePasswordAuthenticationToken для .with(authentication(...)).
    // ПАТТЕРН для order-service: principal = String userId (не Long! JwtAuthFilter так устанавливает).
    // Контроллер делает Long.parseLong(authentication.getName()) — значит тест должен передавать строку.
    //
    // Почему не @WithMockUser? @WithMockUser устанавливает String principal "user" (по умолчанию),
    // и контроллер не может сделать Long.parseLong("user") → NumberFormatException.
    // authentication() позволяет установить любой principal.
    //
    // Почему не jwt()? Требует spring-security-oauth2-resource-server в classpath — его нет.
    private static UsernamePasswordAuthenticationToken auth(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority(role)));
    }

    // ================================================================
    // POST /api/orders/ticket
    // ================================================================

    @Test
    @DisplayName("POST /api/orders/ticket: CLIENT authenticated → 201 with OrderDto")
    void createTicketOrder_clientRole_returns201() throws Exception {
        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(1L).seatRow(3).seatNumber(5).extraServiceIds(new ArrayList<>()).build();

        OrderDto dto = buildOrderDto(100L, 1L, null, "TICKET", "PENDING", new BigDecimal("300.00"));
        // eq(1L) — проверяем что контроллер корректно парсит "1" → 1L (Long.parseLong)
        when(orderService.createTicketOrder(any(TicketOrderRequest.class), eq(1L))).thenReturn(dto);

        // .with(authentication(...)) — устанавливает Security контекст для этого запроса
        mockMvc.perform(post("/api/orders/ticket")
                        .with(authentication(auth("1", "CLIENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())       // HTTP 201
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.orderType").value("TICKET"));
    }

    @Test
    @DisplayName("POST /api/orders/ticket: unauthenticated → 401")
    void createTicketOrder_unauthenticated_returns401() throws Exception {
        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(1L).seatRow(1).seatNumber(1).extraServiceIds(new ArrayList<>()).build();

        // Запрос без .with(authentication(...)) — нет аутентификации
        // HttpStatusEntryPoint в SecurityConfig возвращает 401 (не 403!)
        mockMvc.perform(post("/api/orders/ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized()); // 401 благодаря HttpStatusEntryPoint
    }

    // ================================================================
    // POST /api/orders/ticket/by-seller
    // ================================================================

    @Test
    @DisplayName("POST /api/orders/ticket/by-seller: SELLER role → 201 with OrderDto")
    void createTicketOrderBySeller_sellerRole_returns201() throws Exception {
        SellerTicketOrderRequest req = SellerTicketOrderRequest.builder()
                .clientId(10L).sessionId(2L).seatRow(1).seatNumber(1).extraServiceIds(new ArrayList<>()).build();

        OrderDto dto = buildOrderDto(200L, 10L, 77L, "TICKET", "PAID", new BigDecimal("500.00"));
        when(orderService.createTicketOrderBySeller(any(SellerTicketOrderRequest.class), eq(77L))).thenReturn(dto);

        mockMvc.perform(post("/api/orders/ticket/by-seller")
                        .with(authentication(auth("77", "ROLE_SELLER"))) // ROLE_ prefix required by hasAuthority()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.sellerId").value(77));
    }

    @Test
    @DisplayName("POST /api/orders/ticket/by-seller: CLIENT role → 403 Forbidden")
    void createTicketOrderBySeller_clientRole_returns403() throws Exception {
        SellerTicketOrderRequest req = SellerTicketOrderRequest.builder()
                .clientId(10L).sessionId(2L).seatRow(1).seatNumber(1).extraServiceIds(new ArrayList<>()).build();

        // CLIENT не имеет прав на этот endpoint (@PreAuthorize("hasAuthority('ROLE_SELLER')..."))
        // GlobalExceptionHandler перехватывает Spring Security AccessDeniedException → 403
        mockMvc.perform(post("/api/orders/ticket/by-seller")
                        .with(authentication(auth("1", "CLIENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden()); // 403
    }

    @Test
    @DisplayName("POST /api/orders/ticket/by-seller: ADMIN role → 201 with OrderDto")
    void createTicketOrderBySeller_adminRole_returns201() throws Exception {
        SellerTicketOrderRequest req = SellerTicketOrderRequest.builder()
                .clientId(10L).sessionId(2L).seatRow(1).seatNumber(1).extraServiceIds(new ArrayList<>()).build();

        OrderDto dto = buildOrderDto(201L, 10L, 1L, "TICKET", "PAID", new BigDecimal("500.00"));
        when(orderService.createTicketOrderBySeller(any(SellerTicketOrderRequest.class), eq(1L))).thenReturn(dto);

        // ADMIN тоже может использовать seller endpoint (hasAuthority('ROLE_ADMIN') в @PreAuthorize)
        mockMvc.perform(post("/api/orders/ticket/by-seller")
                        .with(authentication(auth("1", "ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(201));
    }

    // ================================================================
    // POST /api/orders/food
    // ================================================================

    @Test
    @DisplayName("POST /api/orders/food: SELLER role → 201 with OrderDto")
    void createFoodOrder_sellerRole_returns201() throws Exception {
        FoodOrderRequest req = FoodOrderRequest.builder()
                .clientId(20L)
                .items(List.of(FoodOrderRequest.FoodOrderItemRequest.builder().foodItemId(1L).quantity(2).build()))
                .build();

        OrderDto dto = buildOrderDto(300L, 20L, 88L, "FOOD", "PAID", new BigDecimal("500.00"));
        when(orderService.createFoodOrder(any(FoodOrderRequest.class), eq(88L))).thenReturn(dto);

        mockMvc.perform(post("/api/orders/food")
                        .with(authentication(auth("88", "ROLE_SELLER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(300))
                .andExpect(jsonPath("$.orderType").value("FOOD"))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("POST /api/orders/food: CLIENT role → 403 Forbidden")
    void createFoodOrder_clientRole_returns403() throws Exception {
        FoodOrderRequest req = FoodOrderRequest.builder()
                .clientId(20L)
                .items(List.of(FoodOrderRequest.FoodOrderItemRequest.builder().foodItemId(1L).quantity(1).build()))
                .build();

        // CLIENT не может оформить заказ еды через seller endpoint (только /food/client)
        mockMvc.perform(post("/api/orders/food")
                        .with(authentication(auth("1", "CLIENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // GET /api/orders/my
    // ================================================================

    @Test
    @DisplayName("GET /api/orders/my: authenticated CLIENT → 200 with list of orders")
    void getMyOrders_clientRole_returns200() throws Exception {
        List<OrderDto> orders = List.of(
                buildOrderDto(1L, 1L, null, "TICKET", "PAID", new BigDecimal("300.00")),
                buildOrderDto(2L, 1L, null, "FOOD", "PAID", new BigDecimal("150.00"))
        );
        when(orderService.getMyOrders(1L)).thenReturn(orders);

        mockMvc.perform(get("/api/orders/my")
                        .with(authentication(auth("1", "CLIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @DisplayName("GET /api/orders/my: unauthenticated → 401")
    void getMyOrders_unauthenticated_returns401() throws Exception {
        // Без токена — 401 (HttpStatusEntryPoint в SecurityConfig)
        mockMvc.perform(get("/api/orders/my"))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================
    // POST /api/orders/webhook/payment (public)
    // ================================================================

    @Test
    @DisplayName("POST /api/orders/webhook/payment: no auth → 200 (public endpoint)")
    void handlePaymentWebhook_noAuth_returns200() throws Exception {
        PaymentWebhookRequest req = PaymentWebhookRequest.builder()
                .orderId(100L).status("SUCCESS").transactionId("txn-public-test").build();

        // doNothing() — мокируем void-метод (handlePaymentWebhook ничего не возвращает)
        doNothing().when(orderService).handlePaymentWebhook(any(PaymentWebhookRequest.class));

        // Нет .with(authentication(...)) — webhook публичный (permitAll в SecurityConfig)
        mockMvc.perform(post("/api/orders/webhook/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()); // 200 без аутентификации

        // Убеждаемся что сервисный метод был вызван
        verify(orderService).handlePaymentWebhook(any(PaymentWebhookRequest.class));
    }

    @Test
    @DisplayName("POST /api/orders/webhook/payment: FAILED status, no auth → 200 (public endpoint)")
    void handlePaymentWebhook_failedStatus_noAuth_returns200() throws Exception {
        PaymentWebhookRequest req = PaymentWebhookRequest.builder()
                .orderId(101L).status("FAILED").transactionId("txn-fail").build();

        doNothing().when(orderService).handlePaymentWebhook(any(PaymentWebhookRequest.class));

        // FAILED статус тоже обрабатывается публично — любой статус обрабатывается
        mockMvc.perform(post("/api/orders/webhook/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ================================================================
    // GET /api/orders/{id}
    // ================================================================

    @Test
    @DisplayName("GET /api/orders/{id}: CLIENT authenticated → 200 with OrderDto")
    void getOrderById_clientRole_returns200() throws Exception {
        OrderDto dto = buildOrderDto(42L, 1L, null, "TICKET", "PAID", new BigDecimal("300.00"));
        // anyString() для role — контроллер извлекает роль из Authentication
        when(orderService.getOrderById(eq(42L), eq(1L), anyString())).thenReturn(dto);

        mockMvc.perform(get("/api/orders/42")
                        .with(authentication(auth("1", "CLIENT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @DisplayName("GET /api/orders/{id}: SELLER authenticated → 200 with OrderDto")
    void getOrderById_sellerRole_returns200() throws Exception {
        OrderDto dto = buildOrderDto(43L, 10L, 77L, "TICKET", "PAID", new BigDecimal("500.00"));
        when(orderService.getOrderById(eq(43L), eq(77L), anyString())).thenReturn(dto);

        mockMvc.perform(get("/api/orders/43")
                        .with(authentication(auth("77", "SELLER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(43));
    }

    @Test
    @DisplayName("GET /api/orders/{id}: unauthenticated → 401")
    void getOrderById_unauthenticated_returns401() throws Exception {
        // Защищённый endpoint — без токена получаем 401 (не 403!)
        mockMvc.perform(get("/api/orders/42"))
                .andExpect(status().isUnauthorized());
    }
}
