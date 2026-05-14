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

// @SpringBootTest — поднимает ПОЛНЫЙ Spring Context (сервисы, репозитории, JPA, Security).
// WebEnvironment.RANDOM_PORT — встроенный Tomcat запускается на случайном порту.
// Нужен чтобы тестировать реальный HTTP стек (если бы использовали TestRestTemplate).
// В нашем случае мы вызываем сервисы напрямую (@Autowired), порт не используется активно,
// но RANDOM_PORT позволяет нескольким тестам запускаться параллельно без конфликтов.
// properties: отключаем Eureka (не нужен реальный registry) и Cloud Discovery.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
// @ActiveProfiles("test") — загружает application-test.yml:
//   TC-драйвер для datasource, create-drop для ddl-auto, тестовый JWT секрет.
@ActiveProfiles("test")
// @Testcontainers — JUnit 5 расширение, управляет жизненным циклом @Container полей.
@Testcontainers
class OrderServiceIntegrationTest {

    // @Container static — один контейнер PostgreSQL на весь тестовый класс (все методы).
    // static = запускается один раз, не пересоздаётся перед каждым тестом (экономия времени).
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("order_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    // @DynamicPropertySource — устанавливает Spring properties ПЕРЕД созданием ApplicationContext.
    // Переопределяет datasource.url из application-test.yml на реальный URL контейнера.
    // Необходимо потому что порт PostgreSQL в Testcontainers ДИНАМИЧЕСКИЙ (случайный free port).
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);          // реальный TC URL
        registry.add("spring.datasource.username", postgres::getUsername);     // "cinema"
        registry.add("spring.datasource.password", postgres::getPassword);     // "cinema"
        // Явный стандартный драйвер (переопределяет TC-драйвер из application-test.yml)
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    // @MockBean Kafka — не нужен реальный Kafka broker.
    // KafkaTemplate замокирован — вызовы kafkaTemplate.send() записываются в лог Mockito.
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    // @MockBean InternalPaymentService — отключаем async симуляцию оплаты.
    // Без этого simulatePayment() запустил бы Thread.sleep(5000) + HTTP вызов в фоновом потоке.
    @MockBean
    private InternalPaymentService internalPaymentService;

    // @MockBean RestTemplate — замокируем вызовы к hall-service.
    // @LoadBalanced RestTemplate будет замокирован — реальный Eureka не нужен.
    // Каждый тест настраивает нужное поведение через when().thenReturn().
    @MockBean
    private RestTemplate restTemplate;

    // Реальные сервисы из Spring Context — тестируем с реальной PostgreSQL
    @Autowired
    private OrderService orderService;

    @Autowired
    private FoodMenuService foodMenuService;

    // Репозитории для прямой работы с БД в тестах (настройка и проверка данных)
    @Autowired
    private FoodItemRepository foodItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    // Тестовые данные: создаются в @BeforeEach, используются во всех тестах
    private FoodItem savedFoodItem1;
    private FoodItem savedFoodItem2;

    // @BeforeEach: выполняется ПЕРЕД каждым тестом.
    // Очищаем БД и создаём свежие тестовые данные для изоляции тестов.
    @BeforeEach
    void setUp() {
        // Порядок очистки важен: сначала зависимые таблицы (FK constraints).
        // tickets → orders → food_items (ticket зависит от order, order_items от food_items)
        ticketRepository.deleteAll();
        orderRepository.deleteAll();   // CASCADE удаляет order_items
        foodItemRepository.deleteAll();

        // Создаём реальные FoodItem в БД (нужны для food order тестов)
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

        // Настраиваем Kafka mock: fire-and-forget (возвращает null — приемлемо для ListenableFuture)
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(null);

        // InternalPaymentService mock: doNothing() для void метода simulatePayment()
        doNothing().when(internalPaymentService).simulatePayment(anyLong());
    }

    // ================================================================
    // Тест: заказ еды кассиром — персистентность
    // ================================================================

    @Test
    @DisplayName("Integration: createFoodOrder persists to DB and is retrievable via getOrderById")
    void createFoodOrder_andVerifyPersisted() {
        // Arrange
        Long sellerId = 88L;
        Long clientId = 20L;

        // Используем id реальных FoodItem из БД (savedFoodItem1.getId(), savedFoodItem2.getId())
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

        // Act: создаём заказ (реальная транзакция в PostgreSQL)
        OrderDto created = orderService.createFoodOrder(req, sellerId);

        // Assert: DTO корректен
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull(); // id назначен PostgreSQL (не null → INSERT успешен)
        assertThat(created.getStatus()).isEqualTo("PAID");
        assertThat(created.getOrderType()).isEqualTo("FOOD");
        // 250*2 + 150*1 = 650
        assertThat(created.getTotalPrice()).isEqualByComparingTo(new BigDecimal("650.00"));

        // Проверяем персистентность через getOrderById (читаем из БД)
        OrderDto retrieved = orderService.getOrderById(created.getId(), sellerId, "SELLER");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo(created.getId());
        assertThat(retrieved.getStatus()).isEqualTo("PAID");
        assertThat(retrieved.getOrderType()).isEqualTo("FOOD");
        assertThat(retrieved.getItems()).hasSize(2); // 2 позиции сохранились через cascade
        assertThat(retrieved.getSellerId()).isEqualTo(sellerId);
        assertThat(retrieved.getUserId()).isEqualTo(clientId);
    }

    // ================================================================
    // Тест: полный цикл покупки билета (createTicketOrder → webhook SUCCESS)
    // ================================================================

    @Test
    @DisplayName("Integration: createTicketOrder + webhook SUCCESS → order=PAID, ticket created")
    void webhook_fullCycle_success() {
        // Arrange: мокируем ответ hall-service (hall-service не запущен в тесте)
        SessionDto sessionDto = SessionDto.builder()
                .id(1L).hallId(10L).movieId(5L).basePrice(new BigDecimal("400.00")).active(true)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(sessionDto);

        Long userId = 42L;

        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(1L).seatRow(3).seatNumber(7).extraServiceIds(new ArrayList<>()).build();

        // ACT STEP 1: создаём заказ → Order(PENDING) в PostgreSQL
        OrderDto createdOrder = orderService.createTicketOrder(req, userId);
        assertThat(createdOrder).isNotNull();
        Long orderId = createdOrder.getId();
        assertThat(orderId).isNotNull();
        assertThat(createdOrder.getStatus()).isEqualTo("PENDING");
        assertThat(createdOrder.getTotalPrice()).isEqualByComparingTo(new BigDecimal("400.00"));

        // ACT STEP 2: симулируем вебхук оплаты от payment-simulator
        PaymentWebhookRequest webhookReq = PaymentWebhookRequest.builder()
                .orderId(orderId).status("SUCCESS").transactionId("txn-001").build();
        orderService.handlePaymentWebhook(webhookReq);

        // Assert: статус обновился на PAID в PostgreSQL
        OrderDto paidOrder = orderService.getOrderById(orderId, userId, "CLIENT");
        assertThat(paidOrder.getStatus()).isEqualTo("PAID");

        // Assert: билет создан в таблице tickets
        List<com.cinema.order.entity.Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertThat(tickets).hasSize(1);
        assertThat(tickets.get(0).getSessionId()).isEqualTo(1L);
        assertThat(tickets.get(0).getUserId()).isEqualTo(userId);
        assertThat(tickets.get(0).getSeatRow()).isEqualTo(3);
        assertThat(tickets.get(0).getSeatNumber()).isEqualTo(7);
        assertThat(tickets.get(0).getStatus()).isEqualTo(com.cinema.order.entity.TicketStatus.ACTIVE);
        assertThat(tickets.get(0).getQrCode()).isNotBlank(); // QR-код сгенерирован (UUID без тире)
    }

    @Test
    @DisplayName("Integration: createTicketOrder + webhook FAILED → order=CANCELLED, no ticket")
    void webhook_failed_orderCancelled() {
        // Arrange: мокируем hall-service
        SessionDto sessionDto = SessionDto.builder()
                .id(2L).hallId(10L).movieId(5L).basePrice(new BigDecimal("300.00")).active(true)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .build();
        when(restTemplate.getForObject(anyString(), eq(SessionDto.class))).thenReturn(sessionDto);

        Long userId = 55L;

        TicketOrderRequest req = TicketOrderRequest.builder()
                .sessionId(2L).seatRow(1).seatNumber(1).extraServiceIds(new ArrayList<>()).build();

        // Создаём заказ
        OrderDto createdOrder = orderService.createTicketOrder(req, userId);
        Long orderId = createdOrder.getId();
        assertThat(createdOrder.getStatus()).isEqualTo("PENDING");

        // Webhook с FAILED статусом
        PaymentWebhookRequest webhookReq = PaymentWebhookRequest.builder()
                .orderId(orderId).status("FAILED").transactionId("txn-fail").build();
        orderService.handlePaymentWebhook(webhookReq);

        // Assert: заказ отменён
        OrderDto cancelledOrder = orderService.getOrderById(orderId, userId, "CLIENT");
        assertThat(cancelledOrder.getStatus()).isEqualTo("CANCELLED");

        // Assert: билет НЕ создан (оплата не прошла)
        List<com.cinema.order.entity.Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertThat(tickets).isEmpty();
    }

    // ================================================================
    // Тест: FoodMenuService — интеграция с PostgreSQL
    // ================================================================

    @Test
    @DisplayName("Integration: getAllFoodItems returns persisted items from DB")
    void getAllFoodItems_returnsPersisted() {
        // Act: получаем меню из реальной PostgreSQL
        List<FoodItemDto> items = foodMenuService.getAllFoodItems();

        // Assert: в БД есть хотя бы два товара созданных в setUp()
        assertThat(items).hasSizeGreaterThanOrEqualTo(2);
        // anyMatch — хотя бы один элемент соответствует условию (порядок не важен)
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
                .name("Hot Dog").price(new BigDecimal("180.00")).category("SNACK").build();

        // Act: сохраняем новый товар в реальную PostgreSQL
        FoodItemDto saved = foodMenuService.addFoodItem(inputDto);

        // Assert: id назначен (INSERT успешен) и все поля правильные
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Hot Dog");
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat(saved.getCategory()).isEqualTo("SNACK");

        // Проверяем что товар появился в общем списке меню
        List<FoodItemDto> all = foodMenuService.getAllFoodItems();
        assertThat(all).anyMatch(i -> i.getName().equals("Hot Dog"));
    }
}
