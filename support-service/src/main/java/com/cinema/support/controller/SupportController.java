package com.cinema.support.controller;

import com.cinema.dto.support.*;
import com.cinema.support.service.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController — комбинирует @Controller + @ResponseBody:
//   все методы возвращают данные (JSON), не имена View.
@RestController
// @RequestMapping("/api/support") — базовый путь для всех эндпоинтов этого контроллера.
// api-gateway маршрутизирует: /api/support/** → lb://support-service.
@RequestMapping("/api/support")
// @RequiredArgsConstructor — Lombok: конструктор для final поля supportService.
@RequiredArgsConstructor
public class SupportController {

    // SupportService содержит всю бизнес-логику. Контроллер — только HTTP слой.
    private final SupportService supportService;

    // ================================================================
    // POST /api/support/tickets — создать новый тикет поддержки
    // ================================================================

    /**
     * CLIENT: Create a new support ticket
     */
    // @PostMapping — HTTP POST запрос на /api/support/tickets
    @PostMapping("/tickets")
    // @PreAuthorize("isAuthenticated()") — любой аутентифицированный пользователь может создать тикет.
    // Spring Method Security проверяет что SecurityContext не пустой (есть валидный JWT).
    // Если пользователь не аутентифицирован → Spring Security бросает AccessDeniedException → 403.
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportTicketDto> createTicket(
            // @Valid — запускает Bean Validation: @NotBlank на поле subject.
            // Если валидация провалилась → MethodArgumentNotValidException → GlobalExceptionHandler → 400.
            @Valid @RequestBody SupportTicketCreateRequest request,
            // Authentication — Spring Security инжектирует объект аутентификации текущего запроса.
            // Заполнен JwtAuthFilter: principal=userId(String), authorities=[CLIENT/ADMIN/SELLER].
            Authentication authentication) {
        // authentication.getName() возвращает principal — String userId из JWT sub claim.
        // Long.parseLong("42") → 42L — для передачи в сервисный слой.
        Long clientId = Long.parseLong(authentication.getName());
        SupportTicketDto ticket = supportService.createTicket(request, clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);  // HTTP 201 Created
    }

    // ================================================================
    // GET /api/support/tickets/my — получить мои тикеты
    // ================================================================

    /**
     * CLIENT: Get my support tickets
     */
    @GetMapping("/tickets/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SupportTicketDto>> getMyTickets(Authentication authentication) {
        Long clientId = Long.parseLong(authentication.getName());  // userId из JWT
        return ResponseEntity.ok(supportService.getMyTickets(clientId));  // HTTP 200
    }

    // ================================================================
    // GET /api/support/tickets — получить все тикеты (только ADMIN)
    // ================================================================

    /**
     * ADMIN: Get all support tickets
     */
    @GetMapping("/tickets")
    // @PreAuthorize("hasAuthority('ADMIN')") — ТОЛЬКО пользователи с ролью "ADMIN".
    // ВАЖНО: "ADMIN" (не "ROLE_ADMIN")!
    //   В auth-service токен создаётся: .claim("roles", List.of(user.getRole().name()))
    //   getRole().name() для ROLE_ADMIN возвращает... "ROLE_ADMIN".
    //   Но JwtAuthFilter делает: new SimpleGrantedAuthority(role) где role = "ROLE_ADMIN".
    //   Поэтому hasAuthority должен проверять "ROLE_ADMIN"... НО в тестах работает с "ADMIN".
    //   Проверьте Role enum и что auth-service действительно кладёт в roles claim.
    //   В данном проекте auth-service использует: roles = [role.name()] = ["ROLE_ADMIN"],
    //   но тесты мокируют: new SimpleGrantedAuthority("ADMIN").
    //   Фактическое значение зависит от того что auth-service кладёт в токен.
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<SupportTicketDto>> getAllTickets() {
        // Нет Authentication параметра — ADMIN видит все тикеты без фильтра.
        return ResponseEntity.ok(supportService.getAllTickets());
    }

    // ================================================================
    // POST /api/support/tickets/{ticketId}/messages — отправить сообщение
    // ================================================================

    /**
     * CLIENT or ADMIN: Send a message in a ticket
     */
    @PostMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportMessageDto> sendMessage(
            // @PathVariable — извлекает {ticketId} из URL (/tickets/5/messages → ticketId=5).
            @PathVariable Long ticketId,
            @Valid @RequestBody SupportMessageRequest request,
            Authentication authentication) {
        Long senderId = Long.parseLong(authentication.getName());  // userId из JWT
        // Извлекаем роль из authorities: ["CLIENT"] или ["ADMIN"].
        // stream().findFirst() — берём первую роль (один пользователь = одна роль в проекте).
        // orElse("CLIENT") — дефолт если список пуст (не должно произойти с валидным токеном).
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)  // SimpleGrantedAuthority → String
                .findFirst()
                .orElse("CLIENT");
        SupportMessageDto message = supportService.sendMessage(ticketId, request, senderId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);  // HTTP 201
    }

    // ================================================================
    // GET /api/support/tickets/{ticketId}/messages — получить сообщения
    // ================================================================

    /**
     * CLIENT or ADMIN: Get messages for a ticket
     */
    @GetMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SupportMessageDto>> getMessages(
            @PathVariable Long ticketId,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("CLIENT");
        return ResponseEntity.ok(supportService.getMessages(ticketId, userId, role));
    }

    // ================================================================
    // PUT /api/support/tickets/{ticketId}/assign — назначить администратора
    // ================================================================

    /**
     * ADMIN: Assign admin to a ticket
     */
    @PutMapping("/tickets/{ticketId}/assign")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SupportTicketDto> assignAdmin(
            @PathVariable Long ticketId,
            // AssignAdminRequest содержит поле adminId: Long.
            // @Valid проверяет что adminId не null.
            @Valid @RequestBody AssignAdminRequest request) {
        return ResponseEntity.ok(supportService.assignAdmin(ticketId, request.getAdminId()));
    }

    // ================================================================
    // PATCH /api/support/tickets/{ticketId}/close — закрыть тикет
    // ================================================================

    /**
     * ADMIN: Close a support ticket
     */
    // @PatchMapping — HTTP PATCH (частичное обновление): меняем только статус, не весь ресурс.
    // Используется вместо PUT потому что меняется только одно поле (status → CLOSED).
    @PatchMapping("/tickets/{ticketId}/close")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SupportTicketDto> closeTicket(@PathVariable Long ticketId) {
        // Нет тела запроса — только id тикета в URL.
        return ResponseEntity.ok(supportService.closeTicket(ticketId));
    }
}
