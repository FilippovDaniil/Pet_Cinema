package com.cinema.auth.controller;

import com.cinema.auth.dto.UpdateProfileRequest;
import com.cinema.auth.service.AuthService;
import com.cinema.dto.auth.AuthRequest;
import com.cinema.dto.auth.AuthResponse;
import com.cinema.dto.auth.RefreshRequest;
import com.cinema.dto.auth.RegisterRequest;
import com.cinema.dto.auth.UserDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for username: {}", request.getUsername());
        UserDto userDto = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userDto);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login request for username: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("Token refresh request");
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build();
        }
        String refreshToken = authorizationHeader.substring(7);
        log.info("Logout request");
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(authService.getProfile(userId));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserDto> updateMe(
            @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        UserDto updated = authService.updateProfile(userId, request.getUsername(), request.getEmail());
        return ResponseEntity.ok(updated);
    }
}
