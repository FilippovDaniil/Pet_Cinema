package com.cinema.auth.controller; // Пакет контроллеров auth-service

import com.cinema.auth.dto.UpdateProfileRequest;
import com.cinema.auth.service.AuthService;
import com.cinema.dto.auth.AuthRequest;
import com.cinema.dto.auth.AuthResponse;
import com.cinema.dto.auth.RefreshRequest;
import com.cinema.dto.auth.RegisterRequest;
import com.cinema.dto.auth.UserDto;
import jakarta.validation.Valid; // Запускает Bean Validation (проверяет @NotBlank, @Size и т.д. в DTO)
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // Объект аутентификации из SecurityContext
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController         // Все методы возвращают JSON (= @Controller + @ResponseBody)
@RequestMapping("/api/auth") // Базовый путь для всех эндпоинтов этого контроллера
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register") // POST /api/auth/register
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        // @Valid — Spring запускает Bean Validation. При ошибке → GlobalExceptionHandler.handleValidationException
        log.info("Registration request for username: {}", request.getUsername());
        UserDto userDto = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userDto); // HTTP 201 Created
    }

    @PostMapping("/login") // POST /api/auth/login
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login request for username: {}", request.getUsername());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response); // HTTP 200 OK + {accessToken, refreshToken}
    }

    @PostMapping("/refresh") // POST /api/auth/refresh
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        log.info("Token refresh request");
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response); // HTTP 200 OK + новая пара токенов
    }

    @PostMapping("/logout") // POST /api/auth/logout
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorizationHeader) {
        // @RequestHeader("Authorization") — читаем заголовок Authorization
        // При logout клиент передаёт refresh-токен в Authorization (нестандартно, но работает)
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build(); // HTTP 400 Bad Request
        }
        String refreshToken = authorizationHeader.substring(7); // Убираем "Bearer "
        log.info("Logout request");
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build(); // HTTP 204 No Content (успешно, тела нет)
    }

    @GetMapping("/me") // GET /api/auth/me — получить профиль текущего пользователя
    public ResponseEntity<UserDto> getMe(Authentication authentication) {
        // Authentication — объект из SecurityContext, установленный JwtAuthFilter
        // authentication.getName() = userId (строка из sub claim JWT)
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(authService.getProfile(userId));
    }

    @PatchMapping("/me") // PATCH /api/auth/me — обновить профиль (частичное обновление)
    public ResponseEntity<UserDto> updateMe(
            @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        UserDto updated = authService.updateProfile(userId, request.getUsername(), request.getEmail());
        return ResponseEntity.ok(updated);
    }
}
