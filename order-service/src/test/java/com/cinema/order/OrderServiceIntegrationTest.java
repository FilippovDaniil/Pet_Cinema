package com.cinema.order;

import com.cinema.dto.hall.SessionDto;
import com.cinema.dto.order.FoodItemDto;
import com.cinema.dto.order.FoodOrderRequest;
import com.cinema.dto.order.OrderDto;
import com.cinema.dto.order.PaymentWebhookRequest;
import com.cinema.dto.order.TicketOrderRequest;
import com.cinema.order.entity.FoodCategory;
import com.cinema.order.entity.FoodItem;
import com.cinema.order.entity.OrderStatus;
import com.cinema.order.repository.FoodItemRepository;
import com.cinema.order.repository.OrderRepository;
import com.cinema.order.repository.TicketRepository;
import com.cinema.order.service.FoodMenuService;
import com.cinema.order.service.InternalPaymentService;
import com.cinema.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("order_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    // Mock Kafka to avoid real broker requirement
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    // Mock async payment simulation to avoid actual HTTP calls
    @MockBean
    private InternalPaymentService internalPaymentService;

    // Mock load-balanced RestTemplate used to call hall-service
    @MockBean
    private RestTemplate restTemplate;

    @Autowired
    private OrderService orderService;

    @Autowired
    private FoodMenuService foodMenuService;

    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    private FoodItem savedFoodItem1;
    private FoodItem savedFoodItem2;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        orderRepository.deleteAll();
        foodItemRepository.deleteAll();

        // Create real food items in DB
        savedFoodItem1 = foodItemRepository.save(FoodItem.builder()
                .name("Popcorn")
                .price(new BigDecimal("250.00"))
                .category(FoodCategory.POPCORN)
                .build());

        savedFoodItem2 = foodItemRepository.save(FoodItem.builder()
                .name("Cola")
                .price(new BigDecimal("150.00"))
                .category(FoodCategory.DRINK)
                .build());

        // Default Kafka mock (fire-and-forget)
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        // Default InternalPaymentService mock (no-op)
        doNothing().when(internalPaymentService).simulatePayment(anyLong());
    }

    // ----------------------------------------------------------------
    // Food order integration test
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Integration: createFoodOrder persists to DB and is retrievable via getOrderById")
    void createFoodOrder_andVerifyPersisted() {
        // Arrange
        Long sellerId = 88L;
        Long clientId = 20L;

        FoodOrderRequest req = FoodOrderRequest.builder()
                .clientId(clientId)
                .items(List.of(
                        FoodOrderRequest.FoodOrderItemRequest.builder()
                                .foodItemId(savedFoodItem1.getId())
                                .quantity(2)
                                .build(),
                        FoodOrderRequest.FoodOrderItemRequest.builder()
                                .foodItemId(savedFoodItem2.getId())
                                .quantity(1)
                                .build()
                ))
                .build();

        // Act: create food order
        OrderDto created = orderService.createFoodOrder(req, sellerId);

        // Assert: returned DTO is correct
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo("PAID");
        assertThat(created.getOrderType()).isEqualTo("FOOD");
        // totalPrice = 250*2 + 150*1 = 650
        assertThat(created.getTotalPrice()).isEqualByComparingTo(new BigDecimal("650.00"));

        // Verify via getOrderById as SELLER role
        OrderDto retrieved = orderService.getOrderById(created.getId(), sellerId, "SELLER");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getStatus()).isEqualTo("PAID");
        assertThat(retrieved.getOrderType()).isEqualTo("FOOD");
        assertThat(retrieved.getItems()).hasSize(2);
        assertThat(retrieved.getSellerId()).isEqualTo(sellerId);
        assertThat(retrieved.getUserId()).isEqualTo(clientId);
    }

    // ----------------------------------------------------------------
    // Full webhook payment cycle integration test
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Integration: createTicketOrder + webhook SUCCESS → order=PAID, ticket created")
    void webhook_fullCycle_success() {
        // Arrange: mock hall-service REST call
        SessionDto sessionDto = SessionDto.builder()
                .id(1L)
                .hallId(10L)
                .movieId(5L)
                .basePrice(new BigDecimal("400.00"))
                .active(true)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(sessionDto);

        Long userId = 42L;

        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(1L)
                .seatRow(3)
                .seatNumber(7)
                .extraServiceIds(new ArrayList<>())
                .build();

        // Act step 1: create ticket order
        OrderDto createdOrder = orderService.createTicketOrder(req, userId);
        assertThat(createdOrder).isNotNull();
        Long orderId = createdOrder.getId();
        assertThat(orderId).isNotNull();
        assertThat(createdOrder.getStatus()).isEqualTo("PENDING");
        assertThat(createdOrder.getTotalPrice()).isEqualByComparingTo(new BigDecimal("400.00"));

        // Act step 2: simulate payment SUCCESS webhook
        PaymentWebhookRequest webhookReq = PaymentWebhookRequest.builder()
                .orderId(orderId)
                .status("SUCCESS")
                .transactionId("txn-001")
                .build();
        orderService.handlePaymentWebhook(webhookReq);

        // Assert: order is now PAID
        OrderDto paidOrder = orderService.getOrderById(orderId, userId, "CLIENT");
        assertThat(paidOrder.getStatus()).isEqualTo("PAID");

        // Assert: ticket was created in DB
        List<com.cinema.order.entity.Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getSessionId()).isEqualTo(1L);
        assertThat(tickets.get(0).getUserId()).isEqualTo(userId);
        assertThat(tickets.get(0).getSeatRow()).isEqualTo(3);
        assertThat(tickets.get(0).getSeatNumber()).isEqualTo(7);
        assertThat(tickets.get(0).getStatus()).isEqualTo(com.cinema.order.entity.TicketStatus.ACTIVE);
        assertThat(tickets.get(0).getQrCode()).isNotBlank();
    }

    @Test
    @DisplayName("Integration: createTicketOrder + webhook FAILED → order=CANCELLED, no ticket")
    void webhook_failed_orderCancelled() {
        // Arrange: mock hall-service REST call
        SessionDto sessionDto = SessionDto.builder()
                .id(2L)
                .hallId(10L)
                .movieId(5L)
                .basePrice(new BigDecimal("300.00"))
                .active(true)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(sessionDto);

        Long userId = 55L;

        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(2L)
                .seatRow(1)
                .seatNumber(1)
                .extraServiceIds(new ArrayList<>())
                .build();

        // Act step 1: create ticket order
        OrderDto createdOrder = orderService.createTicketOrder(req, userId);
        Long orderId = createdOrder.getId();
        assertThat(createdOrder.getStatus()).isEqualTo("PENDING");

        // Act step 2: simulate payment FAILED webhook
        PaymentWebhookRequest webhookReq = PaymentWebhookRequest.builder()
                .orderId(orderId)
                .status("FAILED")
                .transactionId("txn-fail")
                .build();
        orderService.handlePaymentWebhook(webhookReq);

        // Assert: order is CANCELLED
        OrderDto cancelledOrder = orderService.getOrderById(orderId, userId, "CLIENT");
        assertThat(cancelledOrder.getStatus()).isEqualTo("CANCELLED");

        // Assert: no ticket created
        List<com.cinema.order.entity.Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertThat(tickets).isEmpty();
    }

    // ----------------------------------------------------------------
    // FoodMenuService integration test
    // ----------------------------------------------------------------

    @Test
    @DisplayName("Integration: getAllFoodItems returns persisted items from DB")
    void getAllFoodItems_returnsPersisted() {
        // Act
        List<FoodItemDto> items = foodMenuService.getAllFoodItems();

        // Assert: at least the two items created in setUp are present
        assertThat(items).hasSizeGreaterThanOrEqualTo(2);
        assertThat(items).anyMatch(i -> i.getName().equals("Popcorn")
                && i.getPrice().compareTo(new BigDecimal("250.00")) == 0
                && "POPCORN".equals(i.getCategory()));
        assertThat(items).anyMatch(i -> i.getName().equals("Cola")
                && i.getPrice().compareTo(new BigDecimal("150.00")) == 0
                && "DRINK".equals(i.getCategory()));
    }

    @Test
    @DisplayName("Integration: addFoodItem persists and returns correct DTO")
    void addFoodItem_persistsSuccessfully() {
        // Arrange
        FoodItemDto inputDto = FoodItemDto.builder()
                .name("Hot Dog")
                .price(new BigDecimal("180.00"))
                .category("SNACK")
                .build();

        // Act
        FoodItemDto saved = foodMenuService.addFoodItem(inputDto);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Hot Dog");
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat(saved.getCategory()).isEqualTo("SNACK");

        // Verify it appears in getAllFoodItems
        List<FoodItemDto> all = foodMenuService.getAllFoodItems();
        assertThat(all).anyMatch(i -> i.getName().equals("Hot Dog"));
    }
}
