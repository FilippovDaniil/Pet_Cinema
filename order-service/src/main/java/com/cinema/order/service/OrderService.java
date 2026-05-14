package com.cinema.order.service;

import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.SessionDto;
import com.cinema.dto.order.*;
import com.cinema.order.dto.ClientFoodOrderRequest;
import com.cinema.order.entity.*;
import com.cinema.order.exception.AccessDeniedException;
import com.cinema.order.exception.ResourceNotFoundException;
import com.cinema.order.repository.FoodItemRepository;
import com.cinema.order.repository.OrderRepository;
import com.cinema.order.repository.TicketRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

// @Slf4j — Lombok: генерирует поле log
@Slf4j
// @Service — помечает класс как сервис бизнес-логики, Spring создаёт бин
@Service
// @RequiredArgsConstructor — Lombok: конструктор для всех final полей (DI без @Autowired)
@RequiredArgsConstructor
public class OrderService {

    // Репозиторий для CRUD операций с заказами (таблица orders)
    private final OrderRepository orderRepository;

    // Репозиторий для поиска позиций меню по id (таблица food_items)
    private final FoodItemRepository foodItemRepository;

    // Репозиторий для сохранения и поиска билетов (таблица tickets)
    private final TicketRepository ticketRepository;

    // KafkaTemplate для публикации событий в топики payment-request и ticket-purchase.
    // Ключ String — orderId (для партиционирования), значение Object (Map<String,Object>).
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // @LoadBalanced RestTemplate (primary бин из RestTemplateConfig).
    // Используется для вызовов lb://hall-service — Eureka резолвит в реальный хост:порт.
    private final RestTemplate restTemplate;

    // Сервис асинхронной симуляции оплаты (выполняется в отдельном потоке через @Async)
    private final InternalPaymentService internalPaymentService;

    // Jackson ObjectMapper для сериализации List<Long> extraServiceIds в JSON строку.
    // Spring Boot автоматически создаёт этот бин (Spring MVC его использует для JSON ответов).
    private final ObjectMapper objectMapper;

    // ================================================================
    // CLIENT FLOW: покупка билета через онлайн-форму
    // Поток: POST /api/orders/ticket → Order(PENDING) → Kafka payment-request → @Async webhook
    // ================================================================

    // @Transactional — оборачивает весь метод в транзакцию PostgreSQL.
    // При ошибке (исключении) — автоматический rollback: заказ не сохранится.
    @Transactional
    public OrderDto createTicketOrder(TicketOrderRequest req, Long userId) {
        // 1. Получаем данные сеанса из hall-service через Eureka (lb://hall-service)
        SessionDto session = fetchSession(req.getSessionId());

        // 2. Считаем итоговую цену: basePrice сеанса + цены выбранных доп.услуг
        BigDecimal totalPrice = calculateTicketPrice(session, req.getExtraServiceIds(), session.getHallId());

        // 3. Сериализуем id доп.услуг в JSON для хранения в order_items.ticket_extra_services
        String extraServicesJson = serializeExtraServiceIds(req.getExtraServiceIds());

        // 4. Создаём заказ в статусе PENDING — ожидает подтверждения оплаты
        Order order = Order.builder()
                .userId(userId)                  // кто покупает
                .orderType(OrderType.TICKET)      // тип: билет
                .status(OrderStatus.PENDING)      // ожидает оплаты
                .totalPrice(totalPrice)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())         // пустой список, добавим item ниже
                .build();

        // 5. Создаём позицию заказа (OrderItem типа TICKET)
        OrderItem item = OrderItem.builder()
                .order(order)                                      // ссылка на родительский заказ
                .itemType(ItemType.TICKET)
                .ticketSessionId(req.getSessionId())               // id сеанса
                .ticketSeatRow(req.getSeatRow())                   // ряд места
                .ticketSeatNumber(req.getSeatNumber())             // номер места
                .ticketExtraServices(extraServicesJson)            // JSON доп.услуг
                .quantity(1)                                       // 1 билет
                .price(totalPrice)
                .build();

        // 6. Добавляем позицию и сохраняем заказ.
        // cascade=ALL в Order.items — Hibernate автоматически сохранит OrderItem вместе с Order.
        order.getItems().add(item);
        Order saved = orderRepository.save(order);

        // 7. Публикуем событие в Kafka топик "payment-request".
        // payment-simulator подпишется, подождёт 5 сек и вызовет наш webhook.
        publishPaymentRequest(saved.getId(), userId, totalPrice);

        // 8. Параллельно запускаем локальную симуляцию оплаты (ТОЖЕ через webhook — self-call).
        // В учебном проекте два механизма: payment-simulator (Kafka) и InternalPaymentService (self).
        // @Async — не блокирует: OrderService продолжает выполнение, симуляция работает в фоне.
        internalPaymentService.simulatePayment(saved.getId());

        log.info("Created ticket order {} for user {}", saved.getId(), userId);
        return toDto(saved);
    }

    // ================================================================
    // SELLER FLOW: продажа билета кассиром (немедленная оплата)
    // Поток: POST /api/orders/ticket/by-seller → Order(PAID) + Ticket создаётся сразу
    // ================================================================

    @Transactional
    public OrderDto createTicketOrderBySeller(SellerTicketOrderRequest req, Long sellerId) {
        // Получаем сеанс и рассчитываем цену (аналогично клиентскому потоку)
        SessionDto session = fetchSession(req.getSessionId());
        BigDecimal totalPrice = calculateTicketPrice(session, req.getExtraServiceIds(), session.getHallId());
        String extraServicesJson = serializeExtraServiceIds(req.getExtraServiceIds());

        // Заказ сразу в статусе PAID — кассир принял наличные/карту физически
        Order order = Order.builder()
                .userId(req.getClientId()) // клиент которому продают билет
                .sellerId(sellerId)        // кассир, оформивший продажу
                .orderType(OrderType.TICKET)
                .status(OrderStatus.PAID)  // сразу оплачен
                .totalPrice(totalPrice)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .itemType(ItemType.TICKET)
                .ticketSessionId(req.getSessionId())
                .ticketSeatRow(req.getSeatRow())
                .ticketSeatNumber(req.getSeatNumber())
                .ticketExtraServices(extraServicesJson)
                .quantity(1)
                .price(totalPrice)
                .build();

        order.getItems().add(item);
        Order saved = orderRepository.save(order);

        // Создаём билет немедленно (не нужно ждать оплаты — кассир уже получил деньги)
        Ticket ticket = createTicketFromOrder(saved, item);
        ticketRepository.save(ticket);

        // Уведомляем пользователя о покупке билета через Kafka → notification-service
        publishTicketPurchase(saved, ticket);

        log.info("Seller {} created paid ticket order {} for client {}", sellerId, saved.getId(), req.getClientId());
        return toDto(saved);
    }

    // ================================================================
    // SELLER FLOW: продажа еды кассиром
    // ================================================================

    @Transactional
    public OrderDto createFoodOrder(FoodOrderRequest req, Long sellerId) {
        BigDecimal totalPrice = BigDecimal.ZERO;

        // Создаём заказ сразу PAID (кассир принял деньги)
        Order order = Order.builder()
                .userId(req.getClientId())
                .sellerId(sellerId)
                .orderType(OrderType.FOOD)
                .status(OrderStatus.PAID)
                .totalPrice(BigDecimal.ZERO) // временно 0, обновим после расчёта
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        // Обрабатываем каждую позицию из запроса
        for (FoodOrderRequest.FoodOrderItemRequest itemReq : req.getItems()) {
            // Проверяем что товар существует в меню
            FoodItem foodItem = foodItemRepository.findById(itemReq.getFoodItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Food item not found: " + itemReq.getFoodItemId()));

            // Цена позиции = цена за единицу × количество
            BigDecimal itemTotal = foodItem.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalPrice = totalPrice.add(itemTotal);

            // Создаём OrderItem типа FOOD
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .itemType(ItemType.FOOD)
                    .foodItemId(foodItem.getId())         // скалярный FK (для дублирующего паттерна)
                    .quantity(itemReq.getQuantity())
                    .price(itemTotal)
                    .build();
            order.getItems().add(orderItem);
        }

        // Устанавливаем итоговую цену и сохраняем
        order.setTotalPrice(totalPrice);
        Order saved = orderRepository.save(order);
        log.info("Seller {} created food order {} for client {}", sellerId, saved.getId(), req.getClientId());
        return toDto(saved);
    }

    // ================================================================
    // CLIENT FLOW: заказ еды клиентом онлайн (немедленная оплата)
    // ================================================================

    @Transactional
    public OrderDto createFoodOrderByClient(ClientFoodOrderRequest req, Long userId) {
        BigDecimal totalPrice = BigDecimal.ZERO;

        Order order = Order.builder()
                .userId(userId)
                .orderType(OrderType.FOOD)
                .status(OrderStatus.PAID)  // еда оплачивается сразу (нет асинхронной оплаты)
                .totalPrice(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        for (ClientFoodOrderRequest.ItemRequest itemReq : req.getItems()) {
            FoodItem foodItem = foodItemRepository.findById(itemReq.getFoodItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Food item not found: " + itemReq.getFoodItemId()));

            BigDecimal itemTotal = foodItem.getPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalPrice = totalPrice.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .itemType(ItemType.FOOD)
                    .foodItemId(foodItem.getId())
                    .quantity(itemReq.getQuantity())
                    .price(itemTotal)
                    .build();
            order.getItems().add(orderItem);
        }

        order.setTotalPrice(totalPrice);
        Order saved = orderRepository.save(order);
        log.info("Client {} created food order {}", userId, saved.getId());
        return toDto(saved);
    }

    // ================================================================
    // PAYMENT WEBHOOK: вызывается payment-simulator или InternalPaymentService
    // Эндпоинт: POST /api/orders/webhook/payment (permitAll в SecurityConfig)
    // ================================================================

    @Transactional
    public void handlePaymentWebhook(PaymentWebhookRequest req) {
        // Находим заказ по id из запроса
        Order order = orderRepository.findById(req.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.getOrderId()));

        // equalsIgnoreCase — на случай если payment-simulator пришлёт "success" вместо "SUCCESS"
        if ("SUCCESS".equalsIgnoreCase(req.getStatus())) {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);

            // Ищем позицию типа TICKET в заказе и создаём билет
            order.getItems().stream()
                    .filter(i -> i.getItemType() == ItemType.TICKET)
                    .findFirst()
                    .ifPresent(item -> {
                        Ticket ticket = createTicketFromOrder(order, item);
                        Ticket savedTicket = ticketRepository.save(ticket);
                        // Уведомляем пользователя о покупке
                        publishTicketPurchase(order, savedTicket);
                    });

            log.info("Payment SUCCESS for order {}, transaction {}", req.getOrderId(), req.getTransactionId());
        } else if ("FAILED".equalsIgnoreCase(req.getStatus())) {
            // Оплата не прошла — отменяем заказ
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.info("Payment FAILED for order {}", req.getOrderId());
        }
        // Любой другой статус — игнорируем (логика расширяемости)
    }

    // ================================================================
    // QUERY METHODS: чтение заказов
    // ================================================================

    // readOnly=true — оптимизация: Hibernate не отслеживает изменения (dirty checking выключен),
    // Spring может использовать replica БД если настроено.
    @Transactional(readOnly = true)
    public List<OrderDto> getMyOrders(Long userId) {
        return orderRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrderDto getOrderById(Long id, Long userId, String role) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));

        // Проверяем права доступа:
        // Владелец (userId совпадает) ИЛИ привилегированная роль (SELLER/ADMIN).
        // Проверяем оба формата роли ("ROLE_SELLER" и "SELLER") — контроллер передаёт как есть из токена.
        boolean isOwner = order.getUserId().equals(userId);
        boolean isPrivileged = "ROLE_SELLER".equals(role) || "ROLE_ADMIN".equals(role)
                || "SELLER".equals(role) || "ADMIN".equals(role);

        if (!isOwner && !isPrivileged) {
            throw new AccessDeniedException("You do not have access to this order");
        }

        return toDto(order);
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    // Вызов hall-service для получения данных сеанса: basePrice, hallId.
    // lb://hall-service — Spring Cloud LoadBalancer резолвит в реальный хост через Eureka.
    // Обертка в try-catch: если hall-service недоступен — бросаем 404 вместо 500.
    private SessionDto fetchSession(Long sessionId) {
        try {
            return restTemplate.getForObject(
                    "http://hall-service/api/sessions/" + sessionId,
                    SessionDto.class);
        } catch (Exception e) {
            log.error("Failed to fetch session {}: {}", sessionId, e.getMessage());
            throw new ResourceNotFoundException("Session not found or hall-service unavailable: " + sessionId);
        }
    }

    // Вызов hall-service для получения списка доп.услуг зала.
    // exchange() вместо getForObject() — потому что ответ List<ExtraServiceDto>, не простой объект.
    // ParameterizedTypeReference — способ передать generic тип в RestTemplate (иначе type erasure).
    private List<ExtraServiceDto> fetchExtraServices(Long hallId) {
        try {
            return restTemplate.exchange(
                    "http://hall-service/api/halls/" + hallId + "/extra-services",
                    HttpMethod.GET,
                    null,                                                    // тело запроса не нужно (GET)
                    new ParameterizedTypeReference<List<ExtraServiceDto>>() {}  // тип ответа
            ).getBody();
        } catch (Exception e) {
            // Не критично: если не удалось получить доп.услуги — считаем что их нет
            log.warn("Failed to fetch extra services for hall {}: {}", hallId, e.getMessage());
            return List.of();
        }
    }

    // Расчёт итоговой цены билета: basePrice сеанса + цены выбранных доп.услуг.
    private BigDecimal calculateTicketPrice(SessionDto session, List<Long> extraServiceIds, Long hallId) {
        BigDecimal price = session.getBasePrice() != null ? session.getBasePrice() : BigDecimal.ZERO;

        if (extraServiceIds != null && !extraServiceIds.isEmpty()) {
            List<ExtraServiceDto> extraServices = fetchExtraServices(hallId);
            for (ExtraServiceDto es : extraServices) {
                // Суммируем только те услуги, id которых есть в запросе клиента
                if (extraServiceIds.contains(es.getId())) {
                    price = price.add(es.getPrice());
                }
            }
        }

        return price;
    }

    // Сериализация списка id доп.услуг в JSON строку для хранения в БД.
    // Пример: [1L, 3L] → "[1,3]" (хранится в поле ticket_extra_services TEXT)
    private String serializeExtraServiceIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null; // null сохранится как NULL в БД
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize extra service ids: {}", e.getMessage());
            return null;
        }
    }

    // Создаёт сущность Ticket из заказа и позиции-билета.
    // QR-код — случайный UUID без тире в верхнем регистре (32 символа).
    private Ticket createTicketFromOrder(Order order, OrderItem item) {
        String qrCode = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return Ticket.builder()
                .orderId(order.getId())
                .sessionId(item.getTicketSessionId())
                .userId(order.getUserId())
                .seatRow(item.getTicketSeatRow())
                .seatNumber(item.getTicketSeatNumber())
                .extraServices(item.getTicketExtraServices()) // JSON строка
                .qrCode(qrCode)
                .status(TicketStatus.ACTIVE) // новый билет всегда ACTIVE
                .build();
    }

    // Публикует событие запроса оплаты в Kafka топик "payment-request".
    // Ключ = orderId (строка) — гарантирует что все события для одного заказа
    // попадают в одну партицию (строгий порядок обработки).
    private void publishPaymentRequest(Long orderId, Long userId, BigDecimal amount) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId);
        event.put("userId", userId);
        event.put("amount", amount);
        kafkaTemplate.send("payment-request", String.valueOf(orderId), event);
        log.info("Published payment-request event for order {}", orderId);
    }

    // Публикует событие покупки билета в Kafka топик "ticket-purchase".
    // notification-service подписан на этот топик и создаёт уведомление пользователю.
    private void publishTicketPurchase(Order order, Ticket ticket) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", order.getId());
        event.put("userId", order.getUserId());
        event.put("ticketId", ticket.getId());
        event.put("sessionId", ticket.getSessionId());
        event.put("seatRow", ticket.getSeatRow());
        event.put("seatNumber", ticket.getSeatNumber());
        event.put("qrCode", ticket.getQrCode());
        kafkaTemplate.send("ticket-purchase", String.valueOf(order.getId()), event);
        log.info("Published ticket-purchase event for order {}", order.getId());
    }

    // Конвертирует сущность Order в DTO для отправки клиенту.
    // DTO — Data Transfer Object: только нужные поля, без Hibernate proxy объектов.
    private OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());

        return OrderDto.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .sellerId(order.getSellerId())          // null для клиентских заказов
                .orderType(order.getOrderType().name()) // Enum → String ("TICKET", "FOOD")
                .status(order.getStatus().name())       // Enum → String ("PENDING", "PAID")
                .totalPrice(order.getTotalPrice())
                .items(itemDtos)
                .createdAt(order.getCreatedAt())
                .build();
    }

    // Конвертирует OrderItem в DTO. Работает для обоих типов (TICKET и FOOD).
    // Поля не относящиеся к типу (ticketSeatRow для FOOD) будут 0 или null — это нормально.
    private OrderItemDto toItemDto(OrderItem item) {
        return OrderItemDto.builder()
                .id(item.getId())
                .orderId(item.getOrder() != null ? item.getOrder().getId() : null) // null защита
                .itemType(item.getItemType().name())
                .ticketSessionId(item.getTicketSessionId())
                .ticketSeatRow(item.getTicketSeatRow())
                .ticketSeatNumber(item.getTicketSeatNumber())
                .ticketExtraServices(item.getTicketExtraServices())
                .foodItemId(item.getFoodItemId())
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .build();
    }
}
