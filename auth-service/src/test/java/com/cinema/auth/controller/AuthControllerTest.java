package com.cinema.auth.controller;

import com.cinema.auth.config.SecurityConfig;
import com.cinema.auth.exception.AuthException;
import com.cinema.auth.exception.GlobalExceptionHandler;
import com.cinema.auth.filter.JwtAuthFilter;
import com.cinema.auth.service.AuthService;
import com.cinema.dto.auth.AuthRequest;
import com.cinema.dto.auth.AuthResponse;
import com.cinema.dto.auth.RefreshRequest;
import com.cinema.dto.auth.RegisterRequest;
import com.cinema.dto.auth.UserDto;
import com.fasterxml.jackson.databind.ObjectMapper; // Сериализация Java ↔ JSON в тестах
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest; // Поднимает только web-слой (без БД, Redis, Kafka)
import org.springframework.boot.test.mock.mockito.MockBean; // @MockBean: мок вставляется в Spring Context
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc; // Выполняет HTTP запросы без реального сервера

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;   // Проверяет поля JSON ответа
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;    // Проверяет HTTP статус

@WebMvcTest(
        controllers = AuthController.class, // Тестируем только этот контроллер
        excludeFilters = {
                // Исключаем SecurityConfig и JwtAuthFilter из сканирования —
                // они мешают тестам (требуют RedisTemplate, JwtUtils и т.д.)
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class)
        }
)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
// Импортируем: GlobalExceptionHandler (нужен для проверки форматов ошибок)
// и TestSecurityConfig (разрешает все запросы в тестах)
@ActiveProfiles("test")
@DisplayName("AuthController Web Layer Tests")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc; // Выполняет HTTP запросы в тестовом Spring Context

    @Autowired
    private ObjectMapper objectMapper; // Сериализует объекты в JSON для тела запроса

    @MockBean
    private AuthService authService; // Мок сервиса — контроллер вызывает его методы

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register: valid request → 201 with UserDto body")
    void register_validRequest_returns201WithUserDto() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice").email("alice@example.com").password("secret123").build();

        UserDto userDto = UserDto.builder().id(1L).username("alice")
                .email("alice@example.com").role("ROLE_CLIENT").build();

        when(authService.register(any(RegisterRequest.class))).thenReturn(userDto);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))) // Сериализуем DTO в JSON
                .andExpect(status().isCreated())                    // HTTP 201
                .andExpect(jsonPath("$.id").value(1))               // $.id = 1
                .andExpect(jsonPath("$.username").value("alice"))   // $.username = "alice"
                .andExpect(jsonPath("$.role").value("ROLE_CLIENT")); // $.role = "ROLE_CLIENT"
    }

    @Test
    @DisplayName("POST /api/auth/register: missing username → 400 validation error")
    void register_missingUsername_returns400() throws Exception {
        String body = """
                {
                  "email": "alice@example.com",
                  "password": "secret123"
                }
                """;
        // Text Block (Java 15+): многострочная строка. @Valid → 400 т.к. username = null
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register: missing email → 400 validation error")
    void register_missingEmail_returns400() throws Exception {
        String body = """
                {
                  "username": "alice",
                  "password": "secret123"
                }
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register: invalid email format → 400 validation error")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice").email("not-an-email").password("secret123").build();
        // @Email("not-an-email") → провал Bean Validation → 400

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register: password too short (< 6 chars) → 400 validation error")
    void register_passwordTooShort_returns400() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice").email("alice@example.com").password("abc").build(); // < 6 символов

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register: service throws IllegalArgumentException (duplicate) → 400 with errorCode")
    void register_duplicateUsername_returns400WithErrorCode() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("alice").email("alice@example.com").password("secret123").build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Username 'alice' is already taken"));
        // GlobalExceptionHandler поймает и вернёт {"errorCode":"BAD_REQUEST","message":"..."}

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Username 'alice' is already taken"));
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login: valid credentials → 200 with AuthResponse tokens")
    void login_validRequest_returns200WithAuthResponse() throws Exception {
        AuthRequest request = AuthRequest.builder().username("alice").password("secret123").build();
        AuthResponse response = AuthResponse.builder()
                .accessToken("access-token").refreshToken("refresh-token").build();

        when(authService.login(any(AuthRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    @DisplayName("POST /api/auth/login: missing username → 400 validation error")
    void login_missingUsername_returns400() throws Exception {
        String body = """
                { "password": "secret123" }
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login: missing password → 400 validation error")
    void login_missingPassword_returns400() throws Exception {
        String body = """
                { "username": "alice" }
                """;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login: bad credentials from service → 401 with AUTHENTICATION_ERROR")
    void login_badCredentials_returns401() throws Exception {
        AuthRequest request = AuthRequest.builder().username("alice").password("wrong").build();

        when(authService.login(any(AuthRequest.class)))
                .thenThrow(new AuthException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())                          // HTTP 401
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh: valid token → 200 with new AuthResponse")
    void refresh_validToken_returns200() throws Exception {
        RefreshRequest request = RefreshRequest.builder().refreshToken("old-refresh-token").build();
        AuthResponse response = AuthResponse.builder()
                .accessToken("new-access-token").refreshToken("new-refresh-token").build();

        when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh: blank refreshToken field → 400 validation error")
    void refresh_blankToken_returns400() throws Exception {
        String body = """
                { "refreshToken": "" }
                """; // @NotBlank → пустая строка → 400
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/refresh: missing refreshToken field → 400 validation error")
    void refresh_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/refresh: expired/revoked token from service → 401")
    void refresh_invalidToken_returns401() throws Exception {
        RefreshRequest request = RefreshRequest.builder().refreshToken("invalid-token").build();

        when(authService.refresh(any(RefreshRequest.class)))
                .thenThrow(new AuthException("Refresh token has been revoked"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked"));
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/logout: valid Bearer token header → 204 No Content")
    void logout_validToken_returns204() throws Exception {
        doNothing().when(authService).logout(anyString()); // void метод — ничего не делает

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer some-refresh-token"))
                .andExpect(status().isNoContent()); // HTTP 204
    }

    @Test
    @DisplayName("POST /api/auth/logout: missing Authorization header → 400")
    void logout_missingHeader_returns400() throws Exception {
        // @RequestHeader("Authorization") → MissingRequestHeaderException → GlobalExceptionHandler → 400
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/logout: Authorization header without Bearer prefix → 400")
    void logout_headerWithoutBearerPrefix_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "some-refresh-token")) // Без "Bearer " префикса
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/logout: service throws AuthException (token not found) → 401")
    void logout_tokenNotFound_returns401() throws Exception {
        doThrow(new AuthException("Refresh token not found"))
                .when(authService).logout(anyString());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer ghost-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Refresh token not found"));
    }
}
