package com.cinema.order.service;

import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.SessionDto;
import com.cinema.dto.order.FoodOrderRequest;
import com.cinema.dto.order.OrderDto;
import com.cinema.dto.order.PaymentWebhookRequest;
import com.cinema.dto.order.SellerTicketOrderRequest;
import com.cinema.dto.order.TicketOrderRequest;
import com.cinema.order.entity.FoodCategory;
import com.cinema.order.entity.FoodItem;
import com.cinema.order.entity.ItemType;
import com.cinema.order.entity.Order;
import com.cinema.order.entity.OrderItem;
import com.cinema.order.entity.OrderStatus;
import com.cinema.order.entity.OrderType;
import com.cinema.order.entity.Ticket;
import com.cinema.order.entity.TicketStatus;
import com.cinema.order.exception.AccessDeniedException;
import com.cinema.order.exception.ResourceNotFoundException;
import com.cinema.order.repository.FoodItemRepository;
import com.cinema.order.repository.OrderRepository;
import com.cinema.order.repository.TicketRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Юнит-тесты OrderService. Нет Spring Context, нет БД, нет Kafka — только Mockito.
// @ExtendWith(MockitoExtension.class) — JUnit 5 расширение Mockito:
//   инициализирует @Mock, @InjectMocks, @Captor поля перед каждым тестом.
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    // @Mock — все зависимости OrderService замокированы
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private TicketRepository ticketRepository;

    // KafkaTemplate замокирован — не нужен реальный Kafka broker в юнит-тестах
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    // @LoadBalanced RestTemplate замокирован — не нужен реальный hall-service
    @Mock
    private RestTemplate restTemplate;

    // InternalPaymentService замокирован — не запускаем @Async поток в юнит-тестах
    @Mock
    private InternalPaymentService internalPaymentService;

    // ObjectMapper мокирован — сериализация JSON не тестируется в юнит-тестах
    @Mock
    private ObjectMapper objectMapper;

    // @InjectMocks — Mockito создаёт OrderService и инжектирует все @Mock поля
    @InjectMocks
    private OrderService orderService;

    // @Captor — позволяет захватить аргументы переданные в мок-методы для последующей проверки
    @Captor
    private ArgumentCaptor<Order> orderCaptor;  // для verify(orderRepository).save(orderCaptor.capture())

    @Captor
    private ArgumentCaptor<Ticket> ticketCaptor; // для verify(ticketRepository).save(ticketCaptor.capture())

    @Captor
    private ArgumentCaptor<String> topicCaptor; // для verify(kafkaTemplate).send(topicCaptor.capture(), ...)

    // Вспомогательный метод — создаёт тестовый SessionDto (имитирует ответ hall-service)
    private SessionDto buildSession(Long id, Long hallId, double basePrice) {
        return SessionDto.builder()
                .id(id)
                .hallId(hallId)
                .movieId(5L)
                .basePrice(new BigDecimal(String.valueOf(basePrice)))
                .active(true)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();
    }

    // Вспомогательный метод — создаёт Order с id (имитирует сохранённый в БД заказ)
    private Order buildSavedOrder(Long id, Long userId, OrderStatus status, BigDecimal totalPrice,
                                   OrderType type, List<OrderItem> items) {
        Order order = Order.builder()
                .id(id)
                .userId(userId)
                .orderType(type)
                .status(status)
                .totalPrice(totalPrice)
                .createdAt(LocalDateTime.now())
                .items(items != null ? new ArrayList<>(items) : new ArrayList<>())
                .build();
        return order;
    }

    // ================================================================
    // createTicketOrder tests
    // ================================================================

    @Test
    @DisplayName("createTicketOrder: no extra services → status=PENDING, price=basePrice, kafka payment-request published")
    void createTicketOrder_noExtras_success() {
        // Arrange: мокируем вызов hall-service (RestTemplate.getForObject)
        SessionDto session = buildSession(1L, 10L, 300.0);
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(session);

        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(1L)
                .seatRow(5)
                .seatNumber(3)
                .extraServiceIds(new ArrayList<>()) // нет доп.услуг
                .build();

        // Мок сохранения — возвращает заказ с id=100
        Order savedOrder = buildSavedOrder(100L, 42L, OrderStatus.PENDING,
                new BigDecimal("300.0"), OrderType.TICKET, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderDto result = orderService.createTicketOrder(req, 42L);

        // Assert: проверяем что в save() передан корректный Order
        verify(orderRepository).save(orderCaptor.capture());
        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(capturedOrder.getTotalPrice()).isEqualByComparingTo(new BigDecimal("300.0"));
        assertThat(capturedOrder.getUserId()).isEqualTo(42L);

        // Проверяем что Kafka получил событие в топик "payment-request"
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("payment-request");

        // Проверяем что симуляция оплаты запущена с правильным orderId
        verify(internalPaymentService).simulatePayment(100L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("createTicketOrder: with extras → total price = basePrice + sum of selected extras")
    void createTicketOrder_withExtras_priceCalculated() {
        // Arrange
        SessionDto session = buildSession(1L, 10L, 200.0);
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(session);

        ExtraServiceDto extra1 = ExtraServiceDto.builder().id(1L).hallId(10L).name("3D Glasses").price(new BigDecimal("50")).build();
        ExtraServiceDto extra2 = ExtraServiceDto.builder().id(2L).hallId(10L).name("VIP Seat").price(new BigDecimal("100")).build();

        // Мок вызова для доп.услуг (restTemplate.exchange — возвращает List<ExtraServiceDto>)
        // @SuppressWarnings — подавляем предупреждение об unchecked cast (неизбежно с generic ResponseEntity)
        @SuppressWarnings("unchecked")
        ResponseEntity<List<ExtraServiceDto>> responseEntity = (ResponseEntity<List<ExtraServiceDto>>)
                (ResponseEntity<?>) ResponseEntity.ok(List.of(extra1, extra2));
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class)
        )).thenReturn(responseEntity);

        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(1L)
                .seatRow(3)
                .seatNumber(7)
                .extraServiceIds(List.of(1L, 2L)) // выбраны оба extra
                .build();

        Order savedOrder = buildSavedOrder(101L, 55L, OrderStatus.PENDING,
                new BigDecimal("350"), OrderType.TICKET, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderDto result = orderService.createTicketOrder(req, 55L);

        // Assert: цена = 200 (base) + 50 (extra1) + 100 (extra2) = 350
        verify(orderRepository).save(orderCaptor.capture());
        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getTotalPrice()).isEqualByComparingTo(new BigDecimal("350"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("createTicketOrder: hall-service unavailable → ResourceNotFoundException")
    void createTicketOrder_hallServiceUnavailable() {
        // Arrange: hall-service недоступен — RestTemplate бросает исключение
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class)))
                .thenThrow(new RestClientException("Connection refused"));

        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(99L)
                .seatRow(1)
                .seatNumber(1)
                .extraServiceIds(new ArrayList<>())
                .build();

        // Act & Assert: должно выброситься ResourceNotFoundException с упоминанием sessionId=99
        assertThatThrownBy(() -> orderService.createTicketOrder(req, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        // Ни заказ, ни Kafka событие не должны быть созданы
        verify(orderRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    // ================================================================
    // createTicketOrderBySeller tests
    // ================================================================

    @Test
    @DisplayName("createTicketOrderBySeller: status=PAID immediately, ticket created, kafka ticket-purchase published")
    void createTicketOrderBySeller_success() {
        // Arrange
        SessionDto session = buildSession(2L, 20L, 500.0);
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(session);

        SellerTicketOrderRequest req = SellerTicketOrderRequest.builder()
                .clientId(10L)
                .sessionId(2L)
                .seatRow(1)
                .seatNumber(1)
                .extraServiceIds(new ArrayList<>())
                .build();

        // OrderItem для сохранённого заказа (нужен чтобы createTicketFromOrder отработал)
        OrderItem ticketItem = OrderItem.builder()
                .id(1L)
                .itemType(ItemType.TICKET)
                .ticketSessionId(2L)
                .ticketSeatRow(1)
                .ticketSeatNumber(1)
                .quantity(1)
                .price(new BigDecimal("500.0"))
                .build();

        Order savedOrder = buildSavedOrder(200L, 10L, OrderStatus.PAID,
                new BigDecimal("500.0"), OrderType.TICKET, List.of(ticketItem));
        savedOrder.setSellerId(77L);
        ticketItem.setOrder(savedOrder); // связываем item с order (для toItemDto)

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        Ticket savedTicket = Ticket.builder()
                .id(1L).orderId(200L).sessionId(2L).userId(10L)
                .seatRow(1).seatNumber(1).status(TicketStatus.ACTIVE).qrCode("ABCDEF123456")
                .build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

        // Act
        OrderDto result = orderService.createTicketOrderBySeller(req, 77L);

        // Assert: заказ создан сразу PAID (кассир принял деньги)
        verify(orderRepository).save(orderCaptor.capture());
        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(capturedOrder.getSellerId()).isEqualTo(77L);
        assertThat(capturedOrder.getUserId()).isEqualTo(10L);

        // Ticket создан сразу (не ждём webhook)
        verify(ticketRepository).save(any(Ticket.class));

        // Kafka: топик "ticket-purchase" (не "payment-request" — нет ожидания оплаты)
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("ticket-purchase");

        // InternalPaymentService НЕ должен быть вызван (нет async симуляции для seller flow)
        verify(internalPaymentService, never()).simulatePayment(anyLong());

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getSellerId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("createTicketOrderBySeller: session not found → ResourceNotFoundException")
    void createTicketOrderBySeller_sessionNotFound() {
        // Arrange
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class)))
                .thenThrow(new RestClientException("Timeout"));

        SellerTicketOrderRequest req = SellerTicketOrderRequest.builder()
                .clientId(10L)
                .sessionId(999L)
                .seatRow(1).seatNumber(1)
                .extraServiceIds(new ArrayList<>())
                .build();

        // Act & Assert
        assertThatThrownBy(() -> orderService.createTicketOrderBySeller(req, 77L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        // Ни заказ, ни билет не сохранились
        verify(orderRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
    }

    // ================================================================
    // createFoodOrder tests
    // ================================================================

    @Test
    @DisplayName("createFoodOrder: 2 items → totalPrice = sum of (price * qty), status=PAID, type=FOOD")
    void createFoodOrder_success() {
        // Arrange: два товара в меню
        FoodItem foodItem1 = FoodItem.builder()
                .id(1L).name("Popcorn").price(new BigDecimal("250")).category(FoodCategory.POPCORN)
                .build();
        FoodItem foodItem2 = FoodItem.builder()
                .id(2L).name("Cola").price(new BigDecimal("150")).category(FoodCategory.DRINK)
                .build();

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(foodItem1));
        when(foodItemRepository.findById(2L)).thenReturn(Optional.of(foodItem2));

        FoodOrderRequest req = FoodOrderRequest.builder()
                .clientId(20L)
                .items(List.of(
                        FoodOrderRequest.FoodOrderItemRequest.builder().foodItemId(1L).quantity(2).build(),
                        FoodOrderRequest.FoodOrderItemRequest.builder().foodItemId(2L).quantity(1).build()
                ))
                .build();

        // Мок сохранения с ожидаемыми OrderItem
        OrderItem oi1 = OrderItem.builder()
                .id(10L).itemType(ItemType.FOOD).foodItemId(1L).quantity(2)
                .price(new BigDecimal("500")).build();
        OrderItem oi2 = OrderItem.builder()
                .id(11L).itemType(ItemType.FOOD).foodItemId(2L).quantity(1)
                .price(new BigDecimal("150")).build();

        Order savedOrder = buildSavedOrder(300L, 20L, OrderStatus.PAID,
                new BigDecimal("650"), OrderType.FOOD, List.of(oi1, oi2));
        savedOrder.setSellerId(88L);
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        OrderDto result = orderService.createFoodOrder(req, 88L);

        // Assert: цена = 250*2 + 150*1 = 650
        verify(orderRepository).save(orderCaptor.capture());
        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(capturedOrder.getOrderType()).isEqualTo(OrderType.FOOD);
        assertThat(capturedOrder.getTotalPrice()).isEqualByComparingTo(new BigDecimal("650"));
        assertThat(capturedOrder.getSellerId()).isEqualTo(88L);
        assertThat(capturedOrder.getUserId()).isEqualTo(20L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("PAID");
        assertThat(result.getOrderType()).isEqualTo("FOOD");
        assertThat(result.getTotalPrice()).isEqualByComparingTo(new BigDecimal("650"));
    }

    @Test
    @DisplayName("createFoodOrder: food item not found → ResourceNotFoundException")
    void createFoodOrder_foodItemNotFound() {
        // Arrange: товара с id=999 нет в БД
        when(foodItemRepository.findById(999L)).thenReturn(Optional.empty());

        FoodOrderRequest req = FoodOrderRequest.builder()
                .clientId(20L)
                .items(List.of(
                        FoodOrderRequest.FoodOrderItemRequest.builder().foodItemId(999L).quantity(1).build()
                ))
                .build();

        // Act & Assert: ResourceNotFoundException с упоминанием несуществующего id
        assertThatThrownBy(() -> orderService.createFoodOrder(req, 88L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        // Заказ не сохранился (транзакция откатится)
        verify(orderRepository, never()).save(any());
    }

    // ================================================================
    // handlePaymentWebhook tests
    // ================================================================

    @Test
    @DisplayName("handlePaymentWebhook: SUCCESS → order=PAID, ticket created, kafka ticket-purchase published")
    void handlePaymentWebhook_success() {
        // Arrange: заказ в статусе PENDING с позицией-билетом
        OrderItem ticketItem = OrderItem.builder()
                .id(5L).itemType(ItemType.TICKET).ticketSessionId(1L)
                .ticketSeatRow(3).ticketSeatNumber(7).quantity(1)
                .price(new BigDecimal("300.00")).build();

        Order order = buildSavedOrder(400L, 50L, OrderStatus.PENDING,
                new BigDecimal("300.00"), OrderType.TICKET, List.of(ticketItem));
        ticketItem.setOrder(order);

        when(orderRepository.findById(400L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Ticket savedTicket = Ticket.builder()
                .id(1L).orderId(400L).sessionId(1L).userId(50L)
                .seatRow(3).seatNumber(7).status(TicketStatus.ACTIVE).qrCode("QR123").build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

        PaymentWebhookRequest req = PaymentWebhookRequest.builder()
                .orderId(400L).status("SUCCESS").transactionId("txn-abc-001").build();

        // Act
        orderService.handlePaymentWebhook(req);

        // Assert: статус изменился на PAID (прямая мутация объекта order)
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, times(1)).save(order);

        // Билет создан с корректными полями
        verify(ticketRepository).save(ticketCaptor.capture());
        Ticket capturedTicket = ticketCaptor.getValue();
        assertThat(capturedTicket.getOrderId()).isEqualTo(400L);
        assertThat(capturedTicket.getSessionId()).isEqualTo(1L);
        assertThat(capturedTicket.getStatus()).isEqualTo(TicketStatus.ACTIVE);

        // Kafka уведомление о покупке отправлено
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("ticket-purchase");
    }

    @Test
    @DisplayName("handlePaymentWebhook: FAILED → order=CANCELLED, ticket NOT created, kafka NOT called")
    void handlePaymentWebhook_failure() {
        // Arrange
        OrderItem ticketItem = OrderItem.builder()
                .id(6L).itemType(ItemType.TICKET).ticketSessionId(1L)
                .ticketSeatRow(1).ticketSeatNumber(1).quantity(1)
                .price(new BigDecimal("300.00")).build();

        Order order = buildSavedOrder(401L, 50L, OrderStatus.PENDING,
                new BigDecimal("300.00"), OrderType.TICKET, List.of(ticketItem));
        ticketItem.setOrder(order);

        when(orderRepository.findById(401L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        PaymentWebhookRequest req = PaymentWebhookRequest.builder()
                .orderId(401L).status("FAILED").transactionId("txn-fail-001").build();

        // Act
        orderService.handlePaymentWebhook(req);

        // Assert: заказ отменён
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);

        // Билет и Kafka событие не созданы
        verify(ticketRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("handlePaymentWebhook: order not found → ResourceNotFoundException")
    void handlePaymentWebhook_orderNotFound() {
        // Arrange: заказа с id=9999 нет в БД
        when(orderRepository.findById(9999L)).thenReturn(Optional.empty());

        PaymentWebhookRequest req = PaymentWebhookRequest.builder()
                .orderId(9999L).status("SUCCESS").transactionId("txn-xyz").build();

        // Act & Assert
        assertThatThrownBy(() -> orderService.handlePaymentWebhook(req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9999");

        verify(ticketRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("handlePaymentWebhook: lowercase 'success' status is treated as SUCCESS")
    void handlePaymentWebhook_caseInsensitive() {
        // Arrange: payment-simulator может прислать статус в нижнем регистре
        OrderItem ticketItem = OrderItem.builder()
                .id(7L).itemType(ItemType.TICKET).ticketSessionId(2L)
                .ticketSeatRow(2).ticketSeatNumber(4).quantity(1)
                .price(new BigDecimal("200.00")).build();

        Order order = buildSavedOrder(402L, 60L, OrderStatus.PENDING,
                new BigDecimal("200.00"), OrderType.TICKET, List.of(ticketItem));
        ticketItem.setOrder(order);

        when(orderRepository.findById(402L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Ticket savedTicket = Ticket.builder()
                .id(2L).orderId(402L).sessionId(2L).userId(60L)
                .seatRow(2).seatNumber(4).status(TicketStatus.ACTIVE).qrCode("QRLOWER").build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

        // "success" в нижнем регистре — equalsIgnoreCase должно отработать
        PaymentWebhookRequest req = PaymentWebhookRequest.builder()
                .orderId(402L).status("success").transactionId("txn-lower").build();

        // Act
        orderService.handlePaymentWebhook(req);

        // Assert: несмотря на lowercase статус, заказ оплачен и билет создан
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(ticketRepository).save(any(Ticket.class));
        verify(kafkaTemplate).send(eq("ticket-purchase"), anyString(), any());
    }

    // ================================================================
    // getMyOrders / getOrderById tests
    // ================================================================

    @Test
    @DisplayName("getMyOrders: returns list of DTOs for the user")
    void getMyOrders_returnsUserOrders() {
        // Arrange: два заказа пользователя
        Order order1 = buildSavedOrder(1L, 42L, OrderStatus.PAID, new BigDecimal("300.00"), OrderType.TICKET, List.of());
        Order order2 = buildSavedOrder(2L, 42L, OrderStatus.PENDING, new BigDecimal("150.00"), OrderType.FOOD, List.of());
        when(orderRepository.findByUserId(42L)).thenReturn(List.of(order1, order2));

        // Act
        List<OrderDto> result = orderService.getMyOrders(42L);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getOrderById: owner can access their own order")
    void getOrderById_owner_canAccess() {
        // Arrange: userId совпадает с order.userId (владелец)
        Order order = buildSavedOrder(10L, 42L, OrderStatus.PAID, new BigDecimal("300.00"), OrderType.TICKET, List.of());
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        // Act
        OrderDto result = orderService.getOrderById(10L, 42L, "CLIENT");

        // Assert: доступ разрешён
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUserId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("getOrderById: SELLER role can access any order")
    void getOrderById_seller_canAccess() {
        // Arrange: userId != order.userId, но роль SELLER
        Order order = buildSavedOrder(11L, 100L, OrderStatus.PAID, new BigDecimal("300.00"), OrderType.TICKET, List.of());
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        // Act: другой userId (999 != 100), но SELLER роль
        OrderDto result = orderService.getOrderById(11L, 999L, "SELLER");

        // Assert: SELLER может смотреть любые заказы
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("getOrderById: ADMIN role can access any order")
    void getOrderById_admin_canAccess() {
        // Arrange
        Order order = buildSavedOrder(12L, 100L, OrderStatus.PAID, new BigDecimal("300.00"), OrderType.TICKET, List.of());
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));

        // Act
        OrderDto result = orderService.getOrderById(12L, 888L, "ADMIN");

        // Assert: ADMIN может смотреть любые заказы
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(12L);
    }

    @Test
    @DisplayName("getOrderById: stranger with CLIENT role → AccessDeniedException")
    void getOrderById_stranger_throwsAccessDenied() {
        // Arrange: userId=777 — чужой заказ (владелец userId=100), роль CLIENT
        Order order = buildSavedOrder(13L, 100L, OrderStatus.PAID, new BigDecimal("300.00"), OrderType.TICKET, List.of());
        when(orderRepository.findById(13L)).thenReturn(Optional.of(order));

        // Act & Assert: AccessDeniedException (не ResourceNotFoundException)
        assertThatThrownBy(() -> orderService.getOrderById(13L, 777L, "CLIENT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getOrderById: order not found → ResourceNotFoundException")
    void getOrderById_notFound() {
        // Arrange: заказа не существует
        when(orderRepository.findById(anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> orderService.getOrderById(9999L, 42L, "CLIENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9999");
    }

    // ================================================================
    // Kafka topic name verification tests
    // ================================================================

    @Test
    @DisplayName("Kafka: createTicketOrder publishes to 'payment-request' topic")
    void createTicketOrder_publishesToPaymentRequestTopic() {
        // Arrange
        SessionDto session = buildSession(1L, 10L, 300.0);
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(session);

        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(1L).seatRow(1).seatNumber(1).extraServiceIds(new ArrayList<>()).build();

        Order savedOrder = buildSavedOrder(500L, 1L, OrderStatus.PENDING,
                new BigDecimal("300.0"), OrderType.TICKET, List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        // Act
        orderService.createTicketOrder(req, 1L);

        // Assert: именно "payment-request", не "ticket-purchase"
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("payment-request");
    }

    @Test
    @DisplayName("Kafka: createTicketOrderBySeller publishes to 'ticket-purchase' topic")
    void createTicketOrderBySeller_publishesToTicketPurchaseTopic() {
        // Arrange
        SessionDto session = buildSession(1L, 10L, 300.0);
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(session);

        SellerTicketOrderRequest req = SellerTicketOrderRequest.builder()
                .clientId(1L).sessionId(1L).seatRow(1).seatNumber(1).extraServiceIds(new ArrayList<>())
                .build();

        OrderItem ticketItem = OrderItem.builder()
                .id(20L).itemType(ItemType.TICKET).ticketSessionId(1L)
                .ticketSeatRow(1).ticketSeatNumber(1).quantity(1).price(new BigDecimal("300.0")).build();

        Order savedOrder = buildSavedOrder(501L, 1L, OrderStatus.PAID,
                new BigDecimal("300.0"), OrderType.TICKET, List.of(ticketItem));
        savedOrder.setSellerId(77L);
        ticketItem.setOrder(savedOrder);

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        Ticket savedTicket = Ticket.builder()
                .id(5L).orderId(501L).sessionId(1L).userId(1L)
                .seatRow(1).seatNumber(1).status(TicketStatus.ACTIVE).qrCode("XYZ").build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

        // Act
        orderService.createTicketOrderBySeller(req, 77L);

        // Assert: именно "ticket-purchase" (SELLER поток пропускает payment-request)
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("ticket-purchase");
    }

    @Test
    @DisplayName("Kafka: handlePaymentWebhook SUCCESS publishes to 'ticket-purchase' topic")
    void handlePaymentWebhook_success_publishesToTicketPurchaseTopic() {
        // Arrange
        OrderItem ticketItem = OrderItem.builder()
                .id(30L).itemType(ItemType.TICKET).ticketSessionId(3L)
                .ticketSeatRow(5).ticketSeatNumber(10).quantity(1).price(new BigDecimal("250.00")).build();

        Order order = buildSavedOrder(600L, 70L, OrderStatus.PENDING,
                new BigDecimal("250.00"), OrderType.TICKET, List.of(ticketItem));
        ticketItem.setOrder(order);

        when(orderRepository.findById(600L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Ticket savedTicket = Ticket.builder()
                .id(10L).orderId(600L).sessionId(3L).userId(70L)
                .seatRow(5).seatNumber(10).status(TicketStatus.ACTIVE).qrCode("QRWH").build();
        when(ticketRepository.save(any(Ticket.class))).thenReturn(savedTicket);

        PaymentWebhookRequest req = PaymentWebhookRequest.builder()
                .orderId(600L).status("SUCCESS").transactionId("txn-topic-test").build();

        // Act
        orderService.handlePaymentWebhook(req);

        // Assert: правильный топик после успешной оплаты
        verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), any());
        assertThat(topicCaptor.getValue()).isEqualTo("ticket-purchase");
    }
}
