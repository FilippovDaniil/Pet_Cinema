package com.cinema.auth;

import com.cinema.dto.auth.AuthRequest;
import com.cinema.dto.auth.AuthResponse;
import com.cinema.dto.auth.RefreshRequest;
import com.cinema.dto.auth.RegisterRequest;
import com.cinema.dto.auth.UserDto;
import com.cinema.dto.common.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;          // Поднимает ПОЛНЫЙ Spring Context
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;     // HTTP клиент для интеграционных тестов
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;     // Регистрирует свойства динамически
import org.springframework.test.context.DynamicPropertySource;       // Аннотация для DynamicPropertyRegistry
import org.testcontainers.containers.PostgreSQLContainer;            // Docker-контейнер с PostgreSQL для тестов
import org.testcontainers.junit.jupiter.Container;                   // Поле — Testcontainers контейнер
import org.testcontainers.junit.jupiter.Testcontainers;              // Активирует Testcontainers в JUnit 5

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers // Ищет @Container поля и управляет их жизненным циклом (start/stop)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, // Запускает на случайном порту (нет конфликтов)
        properties = {
                "eureka.client.enabled=false",           // Отключаем Eureka (нет реального реестра в тестах)
                "spring.cloud.discovery.enabled=false"   // Отключаем Service Discovery
        }
)
@ActiveProfiles("test") // Активирует профиль "test" (загружает application-test.yml если есть)
@DisplayName("AuthService Integration Tests")
class AuthServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            // Запускает Docker-контейнер с PostgreSQL 15.
            // static — один контейнер на ВСЕ тесты класса (переиспользуется, не создаётся заново)
            .withDatabaseName("auth_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Подменяем настройки БД: вместо PostgreSQL из application.yml используем тестовый контейнер
        registry.add("spring.datasource.url",              postgres::getJdbcUrl);  // jdbc:postgresql://localhost:PORT/auth_db_test
        registry.add("spring.datasource.username",         postgres::getUsername);
        registry.add("spring.datasource.password",         postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @MockBean
    private StringRedisTemplate stringRedisTemplate;
    // Мокируем Redis — тестам не нужен реальный Redis-сервер.
    // Все hasKey() вернут false (не в blacklist), set() ничего не делает.

    @Autowired
    private TestRestTemplate restTemplate; // HTTP клиент — делает реальные запросы к поднятому серверу

    @BeforeEach
    void configurRedisMock() {
        // Перед каждым тестом настраиваем поведение Redis-мока
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any()); // set() ничего не делает
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false); // Ничего не в blacklist
    }

    // ── Вспомогательные методы ────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String username, String email, String password) {
        return RegisterRequest.builder().username(username).email(email).password(password).build();
    }

    private ResponseEntity<UserDto> doRegister(RegisterRequest req) {
        return restTemplate.postForEntity("/api/auth/register", req, UserDto.class);
    }

    private ResponseEntity<AuthResponse> doLogin(String username, String password) {
        AuthRequest req = AuthRequest.builder().username(username).password(password).build();
        return restTemplate.postForEntity("/api/auth/login", req, AuthResponse.class);
    }

    private ResponseEntity<AuthResponse> doRefresh(String refreshToken) {
        RefreshRequest req = RefreshRequest.builder().refreshToken(refreshToken).build();
        return restTemplate.postForEntity("/api/auth/refresh", req, AuthResponse.class);
    }

    private ResponseEntity<Void> doLogout(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + refreshToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange("/api/auth/logout", HttpMethod.POST, entity, Void.class);
    }

    // ── Полные сценарии (end-to-end через HTTP) ───────────────────────────────

    @Test
    @DisplayName("Full flow: register → login → tokens are valid JWT strings")
    void registerAndLogin_fullFlow() {
        // 1. Регистрация нового пользователя
        RegisterRequest reg = buildRegisterRequest("integrationUser", "integration@example.com", "password123");
        ResponseEntity<UserDto> regResponse = doRegister(reg);

        assertThat(regResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED); // HTTP 201
        UserDto userDto = regResponse.getBody();
        assertThat(userDto.getId()).isPositive();                        // Получил ID из БД
        assertThat(userDto.getRole()).isEqualTo("ROLE_CLIENT");         // Роль по умолчанию

        // 2. Логин с теми же credentials
        ResponseEntity<AuthResponse> loginResponse = doLogin("integrationUser", "password123");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse authResponse = loginResponse.getBody();
        assertThat(authResponse.getAccessToken()).isNotBlank();
        assertThat(authResponse.getRefreshToken()).isNotBlank();

        // 3. Проверка структуры JWT (3 части через точку: header.payload.signature)
        assertThat(authResponse.getAccessToken().split("\\.")).hasSize(3);
        assertThat(authResponse.getRefreshToken().split("\\.")).hasSize(3);
        assertThat(authResponse.getAccessToken()).isNotEqualTo(authResponse.getRefreshToken());
    }

    @Test
    @DisplayName("register: duplicate username → second request returns 400")
    void register_duplicateUsername_returns400() {
        String username = "duplicateUser";
        doRegister(buildRegisterRequest(username, "first@example.com", "password123"));

        ResponseEntity<ErrorResponse> secondResp =
                restTemplate.postForEntity("/api/auth/register",
                        buildRegisterRequest(username, "second@example.com", "password123"),
                        ErrorResponse.class);

        assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondResp.getBody().getMessage()).contains("Username"); // Сообщение об ошибке
    }

    @Test
    @DisplayName("register: duplicate email → second request returns 400")
    void register_duplicateEmail_returns400() {
        String email = "shared@example.com";
        doRegister(buildRegisterRequest("userOne", email, "password123"));

        ResponseEntity<ErrorResponse> secondResp =
                restTemplate.postForEntity("/api/auth/register",
                        buildRegisterRequest("userTwo", email, "password123"),
                        ErrorResponse.class);

        assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondResp.getBody().getMessage()).contains("Email");
    }

    @Test
    @DisplayName("Full refresh flow: register → login → refresh → new valid tokens returned")
    void refresh_flow_returnsNewTokens() {
        doRegister(buildRegisterRequest("refreshUser", "refresh@example.com", "password123"));
        AuthResponse loginTokens = doLogin("refreshUser", "password123").getBody();

        // Обновляем токены через refresh
        ResponseEntity<AuthResponse> refreshResp = doRefresh(loginTokens.getRefreshToken());
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse newTokens = refreshResp.getBody();
        assertThat(newTokens.getAccessToken()).isNotEqualTo(loginTokens.getAccessToken());   // Новый access
        assertThat(newTokens.getRefreshToken()).isNotEqualTo(loginTokens.getRefreshToken()); // Новый refresh
    }

    @Test
    @DisplayName("refresh: same token used twice → second call returns 401 (token revoked after first use)")
    void refresh_sameTokenTwice_secondCallReturns401() {
        doRegister(buildRegisterRequest("rotateUser", "rotate@example.com", "password123"));
        String refreshToken = doLogin("rotateUser", "password123").getBody().getRefreshToken();

        doRefresh(refreshToken); // Первый refresh — успешно, старый токен отзывается

        // Второй refresh с уже отозванным токеном → 401
        ResponseEntity<ErrorResponse> secondRefresh =
                restTemplate.postForEntity("/api/auth/refresh",
                        RefreshRequest.builder().refreshToken(refreshToken).build(),
                        ErrorResponse.class);
        assertThat(secondRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(secondRefresh.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    @DisplayName("Full logout flow: register → login → logout → refresh token is revoked")
    void logout_flow_tokenIsRevoked() {
        doRegister(buildRegisterRequest("logoutUser", "logout@example.com", "password123"));
        String refreshToken = doLogin("logoutUser", "password123").getBody().getRefreshToken();

        ResponseEntity<Void> logoutResp = doLogout(refreshToken);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT); // HTTP 204

        // Пытаемся использовать отозванный токен → 401
        ResponseEntity<ErrorResponse> refreshResp =
                restTemplate.postForEntity("/api/auth/refresh",
                        RefreshRequest.builder().refreshToken(refreshToken).build(),
                        ErrorResponse.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refreshResp.getBody().getMessage()).contains("revoked");
    }

    @Test
    @DisplayName("POST /api/auth/register: missing fields → 400")
    void register_missingFields_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>("{}", headers); // Пустой JSON → все поля null → @NotBlank → 400

        ResponseEntity<ErrorResponse> response =
                restTemplate.exchange("/api/auth/register", HttpMethod.POST, entity, ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/auth/login: wrong password → 401")
    void login_wrongPassword_returns401() {
        doRegister(buildRegisterRequest("loginTest", "logintest@example.com", "correctPassword"));

        ResponseEntity<ErrorResponse> loginResp =
                restTemplate.postForEntity("/api/auth/login",
                        AuthRequest.builder().username("loginTest").password("wrongPassword").build(),
                        ErrorResponse.class);

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginResp.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_ERROR");
    }

    @Test
    @DisplayName("POST /api/auth/refresh: non-existent token → 401")
    void refresh_nonExistentToken_returns401() {
        ResponseEntity<ErrorResponse> response =
                restTemplate.postForEntity("/api/auth/refresh",
                        RefreshRequest.builder().refreshToken("completely-made-up-token").build(),
                        ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("POST /api/auth/logout: missing Authorization header → 400")
    void logout_missingHeader_returns400() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/auth/logout", HttpMethod.POST, HttpEntity.EMPTY, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
