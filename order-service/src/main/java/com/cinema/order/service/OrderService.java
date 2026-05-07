package com.cinema.order.service;

import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.SessionDto;
import com.cinema.dto.order.*;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final FoodItemRepository foodItemRepository;
    private final TicketRepository ticketRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final InternalPaymentService internalPaymentService;
    private final ObjectMapper objectMapper;

    // ---- CLIENT flow: create ticket order (pending payment) ----

    @Transactional
    public OrderDto createTicketOrder(TicketOrderRequest req, Long userId) {
        SessionDto session = fetchSession(req.getSessionId());
        BigDecimal totalPrice = calculateTicketPrice(session, req.getExtraServiceIds(), session.getHallId());

        String extraServicesJson = serializeExtraServiceIds(req.getExtraServiceIds());

        Order order = Order.builder()
                .userId(userId)
                .orderType(OrderType.TICKET)
                .status(OrderStatus.PENDING)
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

        // Publish payment request event
        publishPaymentRequest(saved.getId(), userId, totalPrice);

        // Trigger async payment simulation
        internalPaymentService.simulatePayment(saved.getId());

        log.info("Created ticket order {} for user {}", saved.getId(), userId);
        return toDto(saved);
    }

    // ---- SELLER flow: create ticket order (immediate payment) ----

    @Transactional
    public OrderDto createTicketOrderBySeller(SellerTicketOrderRequest req, Long sellerId) {
        SessionDto session = fetchSession(req.getSessionId());
        BigDecimal totalPrice = calculateTicketPrice(session, req.getExtraServiceIds(), session.getHallId());

        String extraServicesJson = serializeExtraServiceIds(req.getExtraServiceIds());

        Order order = Order.builder()
                .userId(req.getClientId())
                .sellerId(sellerId)
                .orderType(OrderType.TICKET)
                .status(OrderStatus.PAID)
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

        // Create ticket immediately
        Ticket ticket = createTicketFromOrder(saved, item);
        ticketRepository.save(ticket);

        // Publish ticket purchase event
        publishTicketPurchase(saved, ticket);

        log.info("Seller {} created paid ticket order {} for client {}", sellerId, saved.getId(), req.getClientId());
        return toDto(saved);
    }

    // ---- SELLER flow: create food order ----

    @Transactional
    public OrderDto createFoodOrder(FoodOrderRequest req, Long sellerId) {
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;

        Order order = Order.builder()
                .userId(req.getClientId())
                .sellerId(sellerId)
                .orderType(OrderType.FOOD)
                .status(OrderStatus.PAID)
                .totalPrice(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        for (FoodOrderRequest.FoodOrderItemRequest itemReq : req.getItems()) {
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
            orderItems.add(orderItem);
        }

        order.setTotalPrice(totalPrice);
        order.getItems().addAll(orderItems);

        Order saved = orderRepository.save(order);
        log.info("Seller {} created food order {} for client {}", sellerId, saved.getId(), req.getClientId());
        return toDto(saved);
    }

    // ---- Payment webhook handler ----

    @Transactional
    public void handlePaymentWebhook(PaymentWebhookRequest req) {
        Order order = orderRepository.findById(req.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + req.getOrderId()));

        if ("SUCCESS".equalsIgnoreCase(req.getStatus())) {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);

            // Find ticket item and create ticket
            order.getItems().stream()
                    .filter(i -> i.getItemType() == ItemType.TICKET)
                    .findFirst()
                    .ifPresent(item -> {
                        Ticket ticket = createTicketFromOrder(order, item);
                        Ticket savedTicket = ticketRepository.save(ticket);
                        publishTicketPurchase(order, savedTicket);
                    });

            log.info("Payment SUCCESS for order {}, transaction {}", req.getOrderId(), req.getTransactionId());
        } else if ("FAILED".equalsIgnoreCase(req.getStatus())) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.info("Payment FAILED for order {}", req.getOrderId());
        }
    }

    // ---- Query methods ----

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

        boolean isOwner = order.getUserId().equals(userId);
        boolean isPrivileged = "SELLER".equals(role) || "ADMIN".equals(role);

        if (!isOwner && !isPrivileged) {
            throw new AccessDeniedException("You do not have access to this order");
        }

        return toDto(order);
    }

    // ---- Private helpers ----

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

    private List<ExtraServiceDto> fetchExtraServices(Long hallId) {
        try {
            return restTemplate.exchange(
                    "http://hall-service/api/halls/" + hallId + "/extra-services",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ExtraServiceDto>>() {}
            ).getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch extra services for hall {}: {}", hallId, e.getMessage());
            return List.of();
        }
    }

    private BigDecimal calculateTicketPrice(SessionDto session, List<Long> extraServiceIds, Long hallId) {
        BigDecimal price = session.getBasePrice() != null ? session.getBasePrice() : BigDecimal.ZERO;

        if (extraServiceIds != null && !extraServiceIds.isEmpty()) {
            List<ExtraServiceDto> extraServices = fetchExtraServices(hallId);
            for (ExtraServiceDto es : extraServices) {
                if (extraServiceIds.contains(es.getId())) {
                    price = price.add(es.getPrice());
                }
            }
        }

        return price;
    }

    private String serializeExtraServiceIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize extra service ids: {}", e.getMessage());
            return null;
        }
    }

    private Ticket createTicketFromOrder(Order order, OrderItem item) {
        String qrCode = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return Ticket.builder()
                .orderId(order.getId())
                .sessionId(item.getTicketSessionId())
                .userId(order.getUserId())
                .seatRow(item.getTicketSeatRow())
                .seatNumber(item.getTicketSeatNumber())
                .extraServices(item.getTicketExtraServices())
                .qrCode(qrCode)
                .status(TicketStatus.ACTIVE)
                .build();
    }

    private void publishPaymentRequest(Long orderId, Long userId, BigDecimal amount) {
        Map<String, Object> event = new HashMap<>();
        event.put("orderId", orderId);
        event.put("userId", userId);
        event.put("amount", amount);
        kafkaTemplate.send("payment-request", String.valueOf(orderId), event);
        log.info("Published payment-request event for order {}", orderId);
    }

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

    private OrderDto toDto(Order order) {
        List<OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());

        return OrderDto.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .sellerId(order.getSellerId())
                .orderType(order.getOrderType().name())
                .status(order.getStatus().name())
                .totalPrice(order.getTotalPrice())
                .items(itemDtos)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private OrderItemDto toItemDto(OrderItem item) {
        return OrderItemDto.builder()
                .id(item.getId())
                .orderId(item.getOrder() != null ? item.getOrder().getId() : null)
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
