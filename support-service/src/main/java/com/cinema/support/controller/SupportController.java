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

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    /**
     * CLIENT: Create a new support ticket
     */
    @PostMapping("/tickets")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportTicketDto> createTicket(
            @Valid @RequestBody SupportTicketCreateRequest request,
            Authentication authentication) {
        Long clientId = Long.parseLong(authentication.getName());
        SupportTicketDto ticket = supportService.createTicket(request, clientId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ticket);
    }

    /**
     * CLIENT: Get my support tickets
     */
    @GetMapping("/tickets/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SupportTicketDto>> getMyTickets(Authentication authentication) {
        Long clientId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(supportService.getMyTickets(clientId));
    }

    /**
     * ADMIN: Get all support tickets
     */
    @GetMapping("/tickets")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<SupportTicketDto>> getAllTickets() {
        return ResponseEntity.ok(supportService.getAllTickets());
    }

    /**
     * CLIENT or ADMIN: Send a message in a ticket
     */
    @PostMapping("/tickets/{ticketId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportMessageDto> sendMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody SupportMessageRequest request,
            Authentication authentication) {
        Long senderId = Long.parseLong(authentication.getName());
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("CLIENT");
        SupportMessageDto message = supportService.sendMessage(ticketId, request, senderId, role);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

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

    /**
     * ADMIN: Assign admin to a ticket
     */
    @PutMapping("/tickets/{ticketId}/assign")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SupportTicketDto> assignAdmin(
            @PathVariable Long ticketId,
            @Valid @RequestBody AssignAdminRequest request) {
        return ResponseEntity.ok(supportService.assignAdmin(ticketId, request.getAdminId()));
    }

    /**
     * ADMIN: Close a support ticket
     */
    @PatchMapping("/tickets/{ticketId}/close")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<SupportTicketDto> closeTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(supportService.closeTicket(ticketId));
    }
}
