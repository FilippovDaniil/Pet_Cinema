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
import org.mockito.ArgumentCaptor;              // Перехватывает аргументы, переданные в mock-методы
import org.mockito.Captor;
import org.mockito.InjectMocks;                 // Инжектирует @Mock'и в тестируемый объект
import org.mockito.Mock;                        // Создаёт Mockito-мок (заглушку)
import org.mockito.junit.jupiter.MockitoExtension; // Интеграция Mockito с JUnit 5
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
import static org.assertj.core.api.Assertions.assertThatThrownBy; // Проверяет что метод бросает исключение
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;     // Проверяет что метод НЕ был вызван
import static org.mockito.Mockito.times;     // Проверяет сколько раз был вызван метод
import static org.mockito.Mockito.verify;    // Проверяет что метод был вызван с нужными аргументами
import static org.mockito.Mockito.when;      // Настраивает поведение мока

@ExtendWith(MockitoExtension.class) // Активирует Mockito для этого тестового класса
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {
    // Чистые unit-тесты сервиса. Все зависимости — моки. Нет БД, нет Redis, нет Spring Context.

    @Mock private UserRepository userRepository;             // Мок репозитория пользователей
    @Mock private RefreshTokenRepository refreshTokenRepository; // Мок репозитория токенов
    @Mock private PasswordEncoder passwordEncoder;           // Мок BCryptPasswordEncoder
    @Mock private JwtUtils jwtUtils;                         // Мок генератора JWT
    @Mock private AuthenticationManager authenticationManager; // Мок Spring Security AuthManager
    @Mock private StringRedisTemplate redisTemplate;         // Мок Redis
    @Mock private ValueOperations<String, String> valueOperations; // Мок operationsForValue() (SET/GET)

    @InjectMocks
    private AuthService authService; // Тестируемый объект — Mockito вставит все @Mock'и через конструктор

    @Captor private ArgumentCaptor<User> userCaptor;          // Перехватывает User, переданный в save()
    @Captor private ArgumentCaptor<RefreshToken> refreshTokenCaptor; // Перехватывает RefreshToken

    private static final long REFRESH_EXPIRATION = 604_800_000L;
    private static final String BLACKLIST_PREFIX = "blacklist:";

    @BeforeEach
    void setUp() {
        // @Value поля не инжектируются через @InjectMocks — устанавливаем вручную
        ReflectionTestUtils.setField(authService, "refreshExpiration", REFRESH_EXPIRATION);
    }

    // ── register() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: success - unique username and email → saves user with ROLE_CLIENT and returns UserDto")
    void register_success() {
        RegisterRequest req = RegisterRequest.builder()
                .username("alice").email("alice@example.com").password("secret123").build();

        when(userRepository.existsByUsername("alice")).thenReturn(false);      // Логин свободен
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false); // Email свободен
        when(passwordEncoder.encode("secret123")).thenReturn("hashed_secret"); // BCrypt

        User savedUser = User.builder().id(1L).username("alice").email("alice@example.com")
                .password("hashed_secret").role(Role.ROLE_CLIENT).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);      // Мок сохранения

        UserDto result = authService.register(req);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRole()).isEqualTo("ROLE_CLIENT"); // Всегда CLIENT при регистрации

        verify(userRepository).save(userCaptor.capture()); // Проверяем что save() был вызван
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.ROLE_CLIENT); // Роль задана верно
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("hashed_secret"); // Пароль захеширован
    }

    @Test
    @DisplayName("register: duplicate username → throws IllegalArgumentException containing 'Username'")
    void register_duplicateUsername_throwsIllegalArgument() {
        RegisterRequest req = RegisterRequest.builder()
                .username("alice").email("alice@example.com").password("secret123").build();

        when(userRepository.existsByUsername("alice")).thenReturn(true); // Логин занят

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username"); // Сообщение об ошибке содержит "Username"

        verify(userRepository, never()).save(any()); // save() НЕ должен вызываться
        verify(userRepository, never()).existsByEmail(anyString()); // existsByEmail тоже не нужен
    }

    @Test
    @DisplayName("register: duplicate email → throws IllegalArgumentException containing 'Email'")
    void register_duplicateEmail_throwsIllegalArgument() {
        RegisterRequest req = RegisterRequest.builder()
                .username("bob").email("taken@example.com").password("secret123").build();

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true); // Email занят

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: password is encoded before saving")
    void register_passwordIsEncoded() {
        RegisterRequest req = RegisterRequest.builder()
                .username("charlie").email("charlie@example.com").password("rawPassword").build();

        when(userRepository.existsByUsername("charlie")).thenReturn(false);
        when(userRepository.existsByEmail("charlie@example.com")).thenReturn(false);
        when(passwordEncoder.encode("rawPassword")).thenReturn("encoded_pw");

        User saved = User.builder().id(2L).username("charlie").email("charlie@example.com")
                .password("encoded_pw").role(Role.ROLE_CLIENT).build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        authService.register(req);

        verify(passwordEncoder).encode("rawPassword"); // Убеждаемся что encode() был вызван с сырым паролем
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded_pw"); // Сохранён хеш
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: success - authenticates, generates token pair, saves refresh token, returns AuthResponse")
    void login_success() {
        AuthRequest req = AuthRequest.builder().username("alice").password("secret123").build();

        User user = User.builder().id(1L).username("alice").email("alice@example.com")
                .password("hashed").role(Role.ROLE_CLIENT).build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtUtils.generateAccessToken(user)).thenReturn("access-token-value");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("refresh-token-value");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        // thenAnswer(inv -> inv.getArgument(0)) — возвращает первый аргумент как есть (имитирует save())

        AuthResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token-value");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-value");

        // Проверяем что authenticationManager был вызван с правильными credentials
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("alice", "secret123"));

        // Проверяем что refresh-токен сохранён правильно
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        RefreshToken savedToken = refreshTokenCaptor.getValue();
        assertThat(savedToken.getToken()).isEqualTo("refresh-token-value");
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.isRevoked()).isFalse();
        assertThat(savedToken.getExpiryDate()).isAfter(LocalDateTime.now()); // Дата в будущем
    }

    @Test
    @DisplayName("login: bad credentials → throws AuthException")
    void login_badCredentials_throwsAuthException() {
        AuthRequest req = AuthRequest.builder().username("alice").password("wrong").build();

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials")); // Мок бросает исключение

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid username or password"); // Общее сообщение (не раскрываем детали)

        verify(userRepository, never()).findByUsername(anyString()); // findByUsername не должен вызываться
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
        // expiryDate должна быть > now + 6 дней (реально ~7 дней)
        assertThat(refreshTokenCaptor.getValue().getExpiryDate())
                .isAfter(LocalDateTime.now().plusDays(6));
    }

    // ── refresh() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh: success - valid non-revoked non-expired non-blacklisted token → rotates tokens")
    void refresh_success() {
        String oldTokenValue = "old-refresh-token";
        RefreshRequest req = RefreshRequest.builder().refreshToken(oldTokenValue).build();

        User user = User.builder().id(1L).username("alice").email("a@b.com")
                .password("h").role(Role.ROLE_CLIENT).build();

        RefreshToken storedToken = RefreshToken.builder().id(10L).token(oldTokenValue)
                .user(user).expiryDate(LocalDateTime.now().plusDays(7)).revoked(false).build();

        when(refreshTokenRepository.findByToken(oldTokenValue)).thenReturn(Optional.of(storedToken));
        when(redisTemplate.hasKey(BLACKLIST_PREFIX + oldTokenValue)).thenReturn(false); // Не в blacklist
        when(jwtUtils.generateAccessToken(user)).thenReturn("new-access-token");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("new-refresh-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh(req);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");

        assertThat(storedToken.isRevoked()).isTrue(); // Старый токен отозван

        // save() вызвался 2 раза: 1) отозвать старый, 2) сохранить новый
        verify(refreshTokenRepository, times(2)).save(refreshTokenCaptor.capture());
        RefreshToken newToken = refreshTokenCaptor.getAllValues().get(1); // Второй вызов — новый токен
        assertThat(newToken.getToken()).isEqualTo("new-refresh-token");
        assertThat(newToken.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("refresh: revoked token → throws AuthException")
    void refresh_revokedToken_throwsAuthException() {
        RefreshToken storedToken = RefreshToken.builder().id(5L).token("revoked-token")
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(LocalDateTime.now().plusDays(1)).revoked(true).build(); // revoked = true

        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(RefreshRequest.builder().refreshToken("revoked-token").build()))
                .isInstanceOf(AuthException.class).hasMessageContaining("revoked");

        verify(jwtUtils, never()).generateAccessToken(any()); // Новые токены не выданы
    }

    @Test
    @DisplayName("refresh: expired token → throws AuthException")
    void refresh_expiredToken_throwsAuthException() {
        RefreshToken storedToken = RefreshToken.builder().id(6L).token("expired-token")
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(LocalDateTime.now().minusDays(1)).revoked(false).build(); // Истёк вчера

        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refresh(RefreshRequest.builder().refreshToken("expired-token").build()))
                .isInstanceOf(AuthException.class).hasMessageContaining("expired");
    }

    @Test
    @DisplayName("refresh: blacklisted token in Redis → throws AuthException")
    void refresh_blacklistedToken_throwsAuthException() {
        String tokenValue = "blacklisted-token";
        RefreshToken storedToken = RefreshToken.builder().id(7L).token(tokenValue)
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(LocalDateTime.now().plusDays(7)).revoked(false).build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));
        when(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenValue)).thenReturn(true); // В blacklist!

        assertThatThrownBy(() -> authService.refresh(RefreshRequest.builder().refreshToken(tokenValue).build()))
                .isInstanceOf(AuthException.class).hasMessageContaining("blacklisted");
    }

    @Test
    @DisplayName("refresh: token not found in DB → throws AuthException")
    void refresh_tokenNotFound_throwsAuthException() {
        when(refreshTokenRepository.findByToken("ghost-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(RefreshRequest.builder().refreshToken("ghost-token").build()))
                .isInstanceOf(AuthException.class).hasMessageContaining("not found");
    }

    @Test
    @DisplayName("refresh: Redis hasKey returns null (treated as false) → proceeds normally")
    void refresh_redisHasKeyReturnsNull_treatsAsFalse() {
        // Boolean.TRUE.equals(null) == false → null трактуется как "не в blacklist"
        String tokenValue = "token-redis-null";
        User user = User.builder().id(1L).username("alice").email("a@b.com")
                .password("h").role(Role.ROLE_CLIENT).build();
        RefreshToken storedToken = RefreshToken.builder().id(8L).token(tokenValue).user(user)
                .expiryDate(LocalDateTime.now().plusDays(7)).revoked(false).build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));
        when(redisTemplate.hasKey(BLACKLIST_PREFIX + tokenValue)).thenReturn(null); // null
        when(jwtUtils.generateAccessToken(user)).thenReturn("at2");
        when(jwtUtils.generateRefreshToken(user)).thenReturn("rt2");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh(RefreshRequest.builder().refreshToken(tokenValue).build());
        assertThat(response.getAccessToken()).isEqualTo("at2");
    }

    // ── logout() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: success - marks token revoked in DB and adds to Redis blacklist with TTL")
    void logout_success() {
        String tokenValue = "valid-refresh-token";
        LocalDateTime expiryDate = LocalDateTime.now().plusDays(1); // Истекает через 1 день

        RefreshToken storedToken = RefreshToken.builder().id(20L).token(tokenValue)
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(expiryDate).revoked(false).build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations); // Мок для set()

        authService.logout(tokenValue);

        assertThat(storedToken.isRevoked()).isTrue(); // Токен помечен отозванным в БД
        verify(refreshTokenRepository).save(storedToken);

        // Проверяем вызов Redis: SET "blacklist:{token}" "revoked" TTL SECONDS
        verify(valueOperations).set(
                eq(BLACKLIST_PREFIX + tokenValue),
                eq("revoked"),
                anyLong(),          // Любое положительное число секунд
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("logout: token not found → throws AuthException")
    void logout_tokenNotFound_throwsAuthException() {
        when(refreshTokenRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logout("missing-token"))
                .isInstanceOf(AuthException.class).hasMessageContaining("not found");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("logout: token already expired - no Redis set call (TTL would be negative)")
    void logout_alreadyExpiredToken_noRedisSet() {
        // Если токен уже истёк, secondsUntilExpiry <= 0 → не добавляем в Redis (бессмысленно)
        String tokenValue = "expired-logout-token";

        RefreshToken storedToken = RefreshToken.builder().id(21L).token(tokenValue)
                .user(User.builder().id(1L).role(Role.ROLE_CLIENT).build())
                .expiryDate(LocalDateTime.now().minusHours(1)) // Истёк час назад
                .revoked(false).build();

        when(refreshTokenRepository.findByToken(tokenValue)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.logout(tokenValue);

        assertThat(storedToken.isRevoked()).isTrue(); // В БД всё равно помечаем как revoked
        verify(redisTemplate, never()).opsForValue(); // Redis НЕ вызывается
    }
}
