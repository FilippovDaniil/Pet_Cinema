package com.cinema.auth.service;

import com.cinema.auth.entity.RefreshToken;
import com.cinema.auth.entity.Role;
import com.cinema.auth.entity.User;
import com.cinema.auth.exception.AuthException;
import com.cinema.auth.repository.RefreshTokenRepository;
import com.cinema.auth.repository.UserRepository;
import com.cinema.auth.security.JwtUtils;
import com.cinema.dto.auth.AuthRequest;
import com.cinema.dto.auth.AuthResponse;
import com.cinema.dto.auth.RefreshRequest;
import com.cinema.dto.auth.RegisterRequest;
import com.cinema.dto.auth.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    private static final long REFRESH_EXPIRATION = 604_800_000L; // 7 days in ms
    private static final String BLACKLIST_PREFIX = "blacklist:";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpiration", REFRESH_EXPIRATION);
    }

    // =========================================================================
    // register()
    // =========================================================================

    @Test
    @DisplayName("register: success - unique username and email → saves user with ROLE_CLIENT and returns UserDto")
    void register_success() {
        RegisterRequest req = RegisterRequest.builder()
                .username("alice")
                .email("alice@example.com")
                .password("secret123")
                .build();

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed_secret");

        User savedUser = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .password("hashed_secret")
                .role(Role.ROLE_CLIENT)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserDto result = authService.register(req);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getRole()).isEqualTo("ROLE_CLIENT");

        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getRole()).isEqualTo(Role.ROLE_CLIENT);
        assertThat(capturedUser.getPassword()).isEqualTo("hashed_secret");
    }

    @Test
    @DisplayName("register: duplicate username → throws IllegalArgumentException containing 'Username'")
    void register_duplicateUsername_throwsIllegalArgument() {
        RegisterRequest req = RegisterRequest.builder()
                .username("alice")
                .email("alice@example.com")
                .password("secret123")
                .build();

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username");

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    @DisplayName("register: duplicate email → throws IllegalArgumentException containing 'Email'")
    void register_duplicateEmail_throwsIllegalArgument() {
        RegisterRequest req = RegisterRequest.builder()
                .username("bob")
                .email("taken@example.com")
                .password("secret123")
                .build();

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: password is encoded before saving")
    void register_passwordIsEncoded() {
        RegisterRequest req = RegisterRequest.builder()
                .username("charlie")
                .email("charlie@example.com")
                .password("rawPassword")
                .build();

        when(userRepository.existsByUsername("charlie")).thenReturn(false);
        when(userRepository.existsByEmail("charlie@example.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword")).thenReturn("encoded_pw");

        User saved = User.builder()
                .id(2L).username("charlie").email("charlie@example.com")
                .password("encoded_pw").role(Role.ROLE_CLIENT).build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        authService.register(req);

        verify(passwordEncoder).encode("rawPassword");
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded_pw");
    }

    // =========================================================================
    // login()
    // =========================================================================

    @Test
    @DisplayName("login: success - authenticates, generates token pair, saves refresh token, returns AuthResponse")
    void login_success() {
        AuthRequest req = AuthRequest.builder()
                .username("alice")
                .password("secret123")
                .build();

        User user = User.builder()
                .id(1L).username("alice").email("alice@example.com")
                .password("hashed").role(Role.ROLE_CLIENT).build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(user)).thenReturn("access-token-value");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("refresh-token-value");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(req);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token-value");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-value");

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("alice", "secret123"));

        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedToken = refreshTokenCaptor.getValue();
        assertThat(savedToken.getToken()).isEqualTo("refresh-token-value");
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.isRevoked()).isFalse();
        assertThat(savedToken.getExpiryDate()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("login: bad credentials → throws AuthException")
    void login_badCredentials_throwsAuthException() {
        AuthRequest req = AuthRequest.builder()
                .username("alice")
                .password("wrong")
                .build();

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password");

        verify(userRepository, never()).findByUsername(anyString());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("login: refresh token expiry date is set in the future")
    void login_refreshTokenExpiryIsInFuture() {
        AuthRequest req = AuthRequest.builder().username("alice").password("pw").build();
        User user = User.builder().id(1L).username("alice").email("a@b.com")
                .password("h").role(Role.ROLE_CLIENT).build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(user)).thenReturn("at");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("rt");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.login(req);

        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertThat(refreshTokenCaptor.getValue().getExpiryDate())
                .isAfter(LocalDateTime.now().plusDays(6));
    }

    // =========================================================================
    // refresh()
    // =========================================================================

    @Test
    @DisplayName("refresh: success - valid non-revoked non-expired non-blacklisted token → rotates tokens")
    void refresh_success() {
        String oldTokenValue = "old-refresh-token";
        RefreshRequest req = RefreshRequest.builder().refreshToken(oldTokenValue).build();

        User user = User.builder()
                .id(1L).username("alice").email("a@b.com")
                .password("h").role(Role.ROLE_CLIENT).build();

        RefreshToken storedToken = RefreshToken.builder()
                .id(10L)
                .token(oldTokenValue)
                .user(user)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(oldTokenValue)).thenReturn(Optional.of(storedToken));
        when(redisTemplate.hasKey(BLACKLIST_PREFIX + oldTokenValue)).thenReturn(false);
        when(jwtUtils.generateAccessToken(user)).thenReturn("new-access-token");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("new-refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh(req);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");

        // Old token must be revoked
        assertThat(storedToken.isRevoked()).isTrue();
        // New refresh token must be saved
        verify(refreshTokenRepository, times(2)).save(refreshTokenCaptor.capture());
        RefreshToken newToken = refreshTokenCaptor.getAllValues().get(1);
        assertThat(newToken.getToken()).isEqualTo("new-refresh-token");
        assertThat(newToken.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("refresh: revoked token → throws AuthException")
    void refresh_revokedToken_throwsAuthException() {
        String tokenValue = "revoked-token";
        RefreshRequest req = RefreshRequest.builder().refreshToken(tokenValue).build();

        RefreshToken storedToken = RefreshToken.builder()
                .id(5L).token(tokenValue)
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(LocalDateTime.now().plusDays(1))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("revoked");

        verify(jwtUtils, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh: expired token → throws AuthException")
    void refresh_expiredToken_throwsAuthException() {
        String tokenValue = "expired-token";
        RefreshRequest req = RefreshRequest.builder().refreshToken(tokenValue).build();

        RefreshToken storedToken = RefreshToken.builder()
                .id(6L).token(tokenValue)
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(LocalDateTime.now().minusDays(1)) // already expired
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("expired");

        verify(jwtUtils, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh: blacklisted token in Redis → throws AuthException")
    void refresh_blacklistedToken_throwsAuthException() {
        String tokenValue = "blacklisted-token";
        RefreshRequest req = RefreshRequest.builder().refreshToken(tokenValue).build();

        RefreshToken storedToken = RefreshToken.builder()
                .id(7L).token(tokenValue)
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));
        when(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenValue)).thenReturn(true);

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("blacklisted");

        verify(jwtUtils, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh: token not found in DB → throws AuthException")
    void refresh_tokenNotFound_throwsAuthException() {
        RefreshRequest req = RefreshRequest.builder().refreshToken("ghost-token").build();

        when(refreshTokenRepository.findByToken("ghost-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not found");

        verify(jwtUtils, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh: Redis hasKey returns null (treated as false) → proceeds normally")
    void refresh_redisHasKeyReturnsNull_treatsAsFalse() {
        String tokenValue = "token-redis-null";
        RefreshRequest req = RefreshRequest.builder().refreshToken(tokenValue).build();

        User user = User.builder().id(1L).username("alice").email("a@b.com")
                .password("h").role(Role.ROLE_CLIENT).build();

        RefreshToken storedToken = RefreshToken.builder()
                .id(8L).token(tokenValue).user(user)
                .expiryDate(LocalDateTime.now().plusDays(7)).revoked(false).build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));
        when(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenValue)).thenReturn(null); // null → not blacklisted
        when(jwtUtils.generateAccessToken(user)).thenReturn("at2");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("rt2");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh(req);

        assertThat(response.getAccessToken()).isEqualTo("at2");
    }

    // =========================================================================
    // logout()
    // =========================================================================

    @Test
    @DisplayName("logout: success - marks token revoked in DB and adds to Redis blacklist with TTL")
    void logout_success() {
        String tokenValue = "valid-refresh-token";
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(1);

        RefreshToken storedToken = RefreshToken.builder()
                .id(20L)
                .token(tokenValue)
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(expiryDate)
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService.logout(tokenValue);

        // Token is revoked in DB
        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);

        // Token is added to Redis blacklist with positive TTL
        verify(valueOperations).set(
                eq(BLACKLIST_PREFIX + tokenValue),
                eq("revoked"),
                anyLong(),
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("logout: token not found → throws AuthException")
    void logout_tokenNotFound_throwsAuthException() {
        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("missing-token"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("not found");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("logout: token already expired - no Redis set call (TTL would be negative)")
    void logout_alreadyExpiredToken_noRedisSet() {
        String tokenValue = "expired-logout-token";
        LocalDateTime expiryDate = LocalDateTime.now().minusHours(1); // already expired

        RefreshToken storedToken = RefreshToken.builder()
                .id(21L)
                .token(tokenValue)
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(expiryDate)
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.logout(tokenValue);

        // Token is still revoked in DB
        assertThat(storedToken.isRevoked()).isTrue();
        // But Redis is NOT called because TTL is non-positive
        verify(redisTemplate, never()).opsForValue();
    }
}
