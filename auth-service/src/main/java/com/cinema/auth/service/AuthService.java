package com.cinema.auth.service; // Пакет сервисов auth-service

import com.cinema.auth.entity.RefreshToken;
import com.cinema.auth.entity.Role;
import com.cinema.auth.entity.User;
import com.cinema.auth.exception.AuthException;
import com.cinema.auth.exception.ResourceNotFoundException;
import com.cinema.auth.repository.RefreshTokenRepository;
import com.cinema.auth.repository.UserRepository;
import com.cinema.auth.security.JwtUtils;
import com.cinema.dto.auth.AuthRequest;
import com.cinema.dto.auth.AuthResponse;
import com.cinema.dto.auth.RefreshRequest;
import com.cinema.dto.auth.RegisterRequest;
import com.cinema.dto.auth.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;          // Исключение при неверном пароле
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // Объект для передачи логин+пароль в AuthManager
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // @Transactional — операция в рамках одной БД-транзакции

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    // Бизнес-логика аутентификации: регистрация, вход, обновление токенов, выход, профиль.

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;                // BCryptPasswordEncoder
    private final JwtUtils jwtUtils;                              // Генерация/валидация JWT
    private final AuthenticationManager authenticationManager;    // Spring Security: проверяет логин+пароль
    private final StringRedisTemplate redisTemplate;             // Redis: blacklist токенов

    @Value("${jwt.refresh-token-expiration}")
    private long refreshExpiration; // TTL refresh-токена в мс (604800000 = 7 дней)

    private static final String BLACKLIST_PREFIX = "blacklist:";
    // Префикс ключей в Redis. Ключ = "blacklist:{refreshToken}"

    @Transactional // Все операции БД внутри метода выполняются в одной транзакции
    public UserDto register(RegisterRequest req) {
        // Проверяем уникальность логина и email ПЕРЕД сохранением
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username '" + req.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email '" + req.getEmail() + "' is already registered");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword())) // BCrypt хешируем пароль
                .role(Role.ROLE_CLIENT) // Новые пользователи всегда CLIENT (SELLER/ADMIN создаёт только DataLoader)
                .build();

        User savedUser = userRepository.save(user); // INSERT INTO users
        log.info("Registered new user: {}", savedUser.getUsername());

        return mapToUserDto(savedUser); // Возвращаем DTO (без пароля!)
    }

    @Transactional
    public AuthResponse login(AuthRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
            );
            // Если пароль неверный → бросает BadCredentialsException
            // authenticationManager использует UserDetailsServiceImpl.loadUserByUsername()
            // и BCryptPasswordEncoder для сравнения паролей
        } catch (BadCredentialsException e) {
            throw new AuthException("Invalid username or password"); // Скрываем детали для безопасности
        }

        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", req.getUsername()));

        String accessToken = jwtUtils.generateAccessToken(user);       // Новый access-токен (15 мин)
        String refreshTokenValue = jwtUtils.generateRefreshToken(user); // Новый refresh-токен (7 дней)

        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);
        // refreshExpiration в мс → переводим в секунды для LocalDateTime

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiryDate(expiryDate) // Когда токен истечёт в БД
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken); // Сохраняем в refresh_tokens
        log.info("User '{}' logged in successfully", user.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .build();
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String tokenValue = req.getRefreshToken();

        RefreshToken storedToken = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new AuthException("Refresh token not found")); // Токена нет в БД

        if (storedToken.isRevoked()) {
            throw new AuthException("Refresh token has been revoked"); // Уже использован при logout
        }

        if (storedToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new AuthException("Refresh token has expired"); // Истёк по времени
        }

        // Дополнительная проверка Redis blacklist
        String blacklistKey = BLACKLIST_PREFIX + tokenValue;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            throw new AuthException("Refresh token is blacklisted"); // Занесён в blacklist
        }

        User user = storedToken.getUser(); // Получаем владельца токена (FetchType.LAZY → запрос в БД)

        // Ротация токенов: старый отзываем, выдаём новую пару
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken); // UPDATE refresh_tokens SET revoked = true

        String newAccessToken = jwtUtils.generateAccessToken(user);
        String newRefreshTokenValue = jwtUtils.generateRefreshToken(user);

        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenValue)
                .user(user)
                .expiryDate(expiryDate)
                .revoked(false)
                .build();

        refreshTokenRepository.save(newRefreshToken); // INSERT нового токена в БД
        log.info("Tokens refreshed for user '{}'", user.getUsername());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenValue)
                .build();
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new AuthException("Refresh token not found"));

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken); // Помечаем как отозванный в БД

        // Добавляем в Redis blacklist с TTL до истечения токена
        LocalDateTime expiryDate = storedToken.getExpiryDate();
        long secondsUntilExpiry = java.time.Duration.between(LocalDateTime.now(), expiryDate).getSeconds();

        if (secondsUntilExpiry > 0) {
            String blacklistKey = BLACKLIST_PREFIX + refreshTokenValue;
            redisTemplate.opsForValue().set(blacklistKey, "revoked", secondsUntilExpiry, TimeUnit.SECONDS);
            // Redis автоматически удалит ключ через secondsUntilExpiry секунд (TTL)
        }

        log.info("User logged out, refresh token revoked");
    }

    @Transactional(readOnly = true) // readOnly = true → оптимизация: Hibernate не отслеживает изменения
    public UserDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));
        return mapToUserDto(user);
    }

    @Transactional
    public UserDto updateProfile(Long userId, String newUsername, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

        // Обновляем только непустые значения, только если они изменились
        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            if (userRepository.existsByUsername(newUsername)) {
                throw new IllegalArgumentException("Username '" + newUsername + "' is already taken");
            }
            user.setUsername(newUsername);
        }
        if (newEmail != null && !newEmail.isBlank() && !newEmail.equals(user.getEmail())) {
            if (userRepository.existsByEmail(newEmail)) {
                throw new IllegalArgumentException("Email '" + newEmail + "' is already registered");
            }
            user.setEmail(newEmail);
        }

        User saved = userRepository.save(user); // UPDATE users SET username=... WHERE id=...
        log.info("Profile updated for user {}", userId);
        return mapToUserDto(saved);
    }

    private UserDto mapToUserDto(User user) {
        // Преобразует JPA-сущность в DTO (без пароля и служебных полей)
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name()) // Enum → строка "ROLE_CLIENT"
                .build();
    }
}
