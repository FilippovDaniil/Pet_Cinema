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

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * CLIENT: Create a ticket order (payment flow triggered async)
     */
    @PostMapping("/ticket")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDto> createTicketOrder(
            @Valid @RequestBody TicketOrderRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        OrderDto order = orderService.createTicketOrder(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * SELLER: Create a ticket order on behalf of a client (immediate payment)
     */
    @PostMapping("/ticket/by-seller")
    @PreAuthorize("hasAuthority('ROLE_SELLER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<OrderDto> createTicketOrderBySeller(
            @Valid @RequestBody SellerTicketOrderRequest request,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        OrderDto order = orderService.createTicketOrderBySeller(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * SELLER: Create a food order for a client
     */
    @PostMapping("/food")
    @PreAuthorize("hasAuthority('ROLE_SELLER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<OrderDto> createFoodOrder(
            @Valid @RequestBody FoodOrderRequest request,
            Authentication authentication) {
        Long sellerId = Long.parseLong(authentication.getName());
        OrderDto order = orderService.createFoodOrder(request, sellerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * CLIENT: Create a food order (immediate payment)
     */
    @PostMapping("/food/client")
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public ResponseEntity<OrderDto> createFoodOrderByClient(
            @Valid @RequestBody ClientFoodOrderRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        OrderDto order = orderService.createFoodOrderByClient(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * CLIENT: Get my orders
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrderDto>> getMyOrders(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(orderService.getMyOrders(userId));
    }

    /**
     * CLIENT or SELLER: Get order by id
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderDto> getOrderById(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("CLIENT");
        return ResponseEntity.ok(orderService.getOrderById(id, userId, role));
    }

    /**
     * PUBLIC: Payment webhook endpoint
     */
    @PostMapping("/webhook/payment")
    public ResponseEntity<Void> handlePaymentWebhook(
            @Valid @RequestBody PaymentWebhookRequest request) {
        log.info("Received payment webhook for order {}: status={}", request.getOrderId(), request.getStatus());
        orderService.handlePaymentWebhook(request);
        return ResponseEntity.ok().build();
    }
}
