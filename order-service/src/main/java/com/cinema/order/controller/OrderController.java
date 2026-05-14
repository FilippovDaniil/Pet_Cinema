package com.cinema.order.controller;

import com.cinema.dto.order.*;
import com.cinema.order.dto.ClientFoodOrderRequest;
import com.cinema.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @Slf4j — Lombok: генерирует поле log
@Slf4j
// @RestController — @Controller + @ResponseBody: все методы возвращают JSON тело ответа
@RestController
// Базовый путь для всех эндпоинтов контроллера
@RequestMapping("/api/orders")
// @RequiredArgsConstructor — Lombok: конструктор для final поля orderService
@RequiredArgsConstructor
public class OrderController {

    // Сервис бизнес-логики заказов
    private final OrderService orderService;

    // ------------------------------------------------------------------ POST /api/orders/ticket

    // CLIENT поток: покупка билета онлайн (оплата асинхронная через webhook)
    @PostMapping("/ticket")
    // @PreAuthorize — проверяется ДО выполнения метода (благодаря @EnableMethodSecurity).
    // isAuthenticated() — достаточно любой роли, главное наличие валидного JWT токена.
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDto> createTicketOrder(
            @Valid @RequestBody TicketOrderRequest request, // @Valid — запускает Bean Validation
            Authentication authentication) {               // Spring инжектирует из SecurityContext
        // authentication.getName() — возвращает principal = userId (String, установлен в JwtAuthFilter)
        Long userId = Long.parseLong(authentication.getName());
        OrderDto order = orderService.createTicketOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order); // HTTP 201 Created
    }

    // ------------------------------------------------------------------ POST /api/orders/ticket/by-seller

    // SELLER поток: продажа билета кассиром (оплата мгновенная)
    @PostMapping("/ticket/by-seller")
    // Только SELLER или ADMIN могут использовать этот эндпоинт
    @PreAuthorize("hasAuthority('ROLE_SELLER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<OrderDto> createTicketOrderBySeller(
            @Valid @RequestBody SellerTicketOrderRequest request,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        OrderDto order = orderService.createTicketOrderBySeller(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // ------------------------------------------------------------------ POST /api/orders/food

    // SELLER поток: продажа еды кассиром клиенту
    @PostMapping("/food")
    @PreAuthorize("hasAuthority('ROLE_SELLER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<OrderDto> createFoodOrder(
            @Valid @RequestBody FoodOrderRequest request,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        OrderDto order = orderService.createFoodOrder(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // ------------------------------------------------------------------ POST /api/orders/food/client

    // CLIENT поток: заказ еды клиентом онлайн (оплата мгновенная)
    @PostMapping("/food/client")
    // Только ROLE_CLIENT — не SELLER (SELLER использует /food endpoint выше)
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public ResponseEntity<OrderDto> createFoodOrderByClient(
            @Valid @RequestBody ClientFoodOrderRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        OrderDto order = orderService.createFoodOrderByClient(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // ------------------------------------------------------------------ GET /api/orders/my

    // История заказов текущего пользователя
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrderDto>> getMyOrders(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(orderService.getMyOrders(userId));
    }

    // ------------------------------------------------------------------ GET /api/orders/{id}

    // Получить заказ по id (владелец или SELLER/ADMIN)
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDto> getOrderById(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());

        // Извлекаем роль из authorities — передаём в сервис для проверки прав.
        // getAuthorities() возвращает Collection, берём первую (у нас всегда одна роль).
        // orElse("CLIENT") — fallback если вдруг authorities пустые.
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority) // SimpleGrantedAuthority.getAuthority() → "ROLE_CLIENT"
                .findFirst()
                .orElse("CLIENT");
        return ResponseEntity.ok(orderService.getOrderById(id, userId, role));
    }

    // ------------------------------------------------------------------ POST /api/orders/webhook/payment

    // Публичный эндпоинт для вебхука оплаты (permitAll в SecurityConfig).
    // Вызывается payment-simulator (через Kafka consumer) и InternalPaymentService (self-call).
    // Нет @PreAuthorize — защита обеспечивается на уровне SecurityConfig: .permitAll()
    @PostMapping("/webhook/payment")
    public ResponseEntity<Void> handlePaymentWebhook(
            @Valid @RequestBody PaymentWebhookRequest request) {
        log.info("Received payment webhook for order {}: status={}", request.getOrderId(), request.getStatus());
        orderService.handlePaymentWebhook(request);
        return ResponseEntity.ok().build(); // HTTP 200 OK (без тела)
    }
}
