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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@ActiveProfiles("test")
@DisplayName("AuthService Integration Tests")
class AuthServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("auth_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    /**
     * Mock the Redis template so the integration tests do not require a real Redis
     * instance. All hasKey calls return false (not blacklisted) by default, and
     * opsForValue() returns a mock ValueOperations.
     */
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void configurRedisMock() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RegisterRequest buildRegisterRequest(String username, String email, String password) {
        return RegisterRequest.builder()
                .username(username)
                .email(email)
                .password(password)
                .build();
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

    // =========================================================================
    // Full register → login → validate flow
    // =========================================================================

    @Test
    @DisplayName("Full flow: register → login → tokens are valid JWT strings")
    void registerAndLogin_fullFlow() {
        // 1. Register
        RegisterRequest reg = buildRegisterRequest("integrationUser", "integration@example.com", "password123");
        ResponseEntity<UserDto> regResponse = doRegister(reg);

        assertThat(regResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserDto userDto = regResponse.getBody();
        assertThat(userDto).isNotNull();
        assertThat(userDto.getId()).isPositive();
        assertThat(userDto.getUsername()).isEqualTo("integrationUser");
        assertThat(userDto.getEmail()).isEqualTo("integration@example.com");
        assertThat(userDto.getRole()).isEqualTo("ROLE_CLIENT");

        // 2. Login
        ResponseEntity<AuthResponse> loginResponse = doLogin("integrationUser", "password123");

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuthResponse authResponse = loginResponse.getBody();
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isNotBlank();
        assertThat(authResponse.getRefreshToken()).isNotBlank();

        // 3. Validate token structure (3 JWT parts separated by dots)
        String accessToken = authResponse.getAccessToken();
        String refreshToken = authResponse.getRefreshToken();
        assertThat(accessToken.split("\\.")).hasSize(3);
        assertThat(refreshToken.split("\\.")).hasSize(3);
        assertThat(accessToken).isNotEqualTo(refreshToken);
    }

    // =========================================================================
    // Duplicate registration
    // =========================================================================

    @Test
    @DisplayName("register: duplicate username → second request returns 400")
    void register_duplicateUsername_returns400() {
        String username = "duplicateUser";
        RegisterRequest first = buildRegisterRequest(username, "first@example.com", "password123");
        RegisterRequest second = buildRegisterRequest(username, "second@example.com", "password123");

        ResponseEntity<UserDto> firstResp = doRegister(first);
        assertThat(firstResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ErrorResponse> secondResp =
                restTemplate.postForEntity("/api/auth/register", second, ErrorResponse.class);
        assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondResp.getBody()).isNotNull();
        assertThat(secondResp.getBody().getMessage()).contains("Username");
    }

    @Test
    @DisplayName("register: duplicate email → second request returns 400")
    void register_duplicateEmail_returns400() {
        String email = "shared@example.com";
        RegisterRequest first = buildRegisterRequest("userOne", email, "password123");
        RegisterRequest second = buildRegisterRequest("userTwo", email, "password123");

        ResponseEntity<UserDto> firstResp = doRegister(first);
        assertThat(firstResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ErrorResponse> secondResp =
                restTemplate.postForEntity("/api/auth/register", second, ErrorResponse.class);
        assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(secondResp.getBody()).isNotNull();
        assertThat(secondResp.getBody().getMessage()).contains("Email");
    }

    // =========================================================================
    // Token refresh flow
    // =========================================================================

    @Test
    @DisplayName("Full refresh flow: register → login → refresh → new valid tokens returned")
    void refresh_flow_returnsNewTokens() {
        // Register & login
        RegisterRequest reg = buildRegisterRequest("refreshUser", "refresh@example.com", "password123");
        doRegister(reg);

        ResponseEntity<AuthResponse> loginResp = doLogin("refreshUser", "password123");
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String oldRefreshToken = loginResp.getBody().getRefreshToken();
        String oldAccessToken = loginResp.getBody().getAccessToken();

        // Refresh
        ResponseEntity<AuthResponse> refreshResp = doRefresh(oldRefreshToken);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse newTokens = refreshResp.getBody();
        assertThat(newTokens).isNotNull();
        assertThat(newTokens.getAccessToken()).isNotBlank();
        assertThat(newTokens.getRefreshToken()).isNotBlank();
        // New tokens must be different from the old ones
        assertThat(newTokens.getAccessToken()).isNotEqualTo(oldAccessToken);
        assertThat(newTokens.getRefreshToken()).isNotEqualTo(oldRefreshToken);
    }

    @Test
    @DisplayName("refresh: same token used twice → second call returns 401 (token revoked after first use)")
    void refresh_sameTokenTwice_secondCallReturns401() {
        RegisterRequest reg = buildRegisterRequest("rotateUser", "rotate@example.com", "password123");
        doRegister(reg);

        ResponseEntity<AuthResponse> loginResp = doLogin("rotateUser", "password123");
        String refreshToken = loginResp.getBody().getRefreshToken();

        // First refresh - should succeed
        ResponseEntity<AuthResponse> firstRefresh = doRefresh(refreshToken);
        assertThat(firstRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second refresh with the same (now revoked) token - should fail
        ResponseEntity<ErrorResponse> secondRefresh =
                restTemplate.postForEntity("/api/auth/refresh",
                        RefreshRequest.builder().refreshToken(refreshToken).build(),
                        ErrorResponse.class);
        assertThat(secondRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(secondRefresh.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_ERROR");
    }

    // =========================================================================
    // Logout flow
    // =========================================================================

    @Test
    @DisplayName("Full logout flow: register → login → logout → refresh token is revoked")
    void logout_flow_tokenIsRevoked() {
        RegisterRequest reg = buildRegisterRequest("logoutUser", "logout@example.com", "password123");
        doRegister(reg);

        ResponseEntity<AuthResponse> loginResp = doLogin("logoutUser", "password123");
        String refreshToken = loginResp.getBody().getRefreshToken();

        // Logout
        ResponseEntity<Void> logoutResp = doLogout(refreshToken);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Attempt refresh with revoked token → 401
        ResponseEntity<ErrorResponse> refreshResp =
                restTemplate.postForEntity("/api/auth/refresh",
                        RefreshRequest.builder().refreshToken(refreshToken).build(),
                        ErrorResponse.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refreshResp.getBody().getMessage()).contains("revoked");
    }

    // =========================================================================
    // Validation on endpoints
    // =========================================================================

    @Test
    @DisplayName("POST /api/auth/register: missing fields → 400")
    void register_missingFields_returns400() {
        String body = "{}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<ErrorResponse> response =
                restTemplate.exchange("/api/auth/register", HttpMethod.POST, entity, ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("POST /api/auth/login: wrong password → 401")
    void login_wrongPassword_returns401() {
        RegisterRequest reg = buildRegisterRequest("loginTest", "logintest@example.com", "correctPassword");
        doRegister(reg);

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
        ResponseEntity<Void> response =
                restTemplate.exchange("/api/auth/logout", HttpMethod.POST,
                        HttpEntity.EMPTY, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
