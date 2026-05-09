package com.cinema.auth.service;

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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshExpiration;

    private static final String BLACKLIST_PREFIX = "blacklist:";

    @Transactional
    public UserDto register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("Username '" + req.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email '" + req.getEmail() + "' is already registered");
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(Role.ROLE_CLIENT)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user: {}", savedUser.getUsername());

        return mapToUserDto(savedUser);
    }

    @Transactional
    public AuthResponse login(AuthRequest req) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
            );
        } catch (BadCredentialsException e) {
            throw new AuthException("Invalid username or password");
        }

        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", req.getUsername()));

        String accessToken = jwtUtils.generateAccessToken(user);
        String refreshTokenValue = jwtUtils.generateRefreshToken(user);

        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiryDate(expiryDate)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
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
                .orElseThrow(() -> new AuthException("Refresh token not found"));

        if (storedToken.isRevoked()) {
            throw new AuthException("Refresh token has been revoked");
        }

        if (storedToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new AuthException("Refresh token has expired");
        }

        // Check Redis blacklist
        String blacklistKey = BLACKLIST_PREFIX + tokenValue;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey))) {
            throw new AuthException("Refresh token is blacklisted");
        }

        User user = storedToken.getUser();

        // Revoke old token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Generate new token pair
        String newAccessToken = jwtUtils.generateAccessToken(user);
        String newRefreshTokenValue = jwtUtils.generateRefreshToken(user);

        LocalDateTime expiryDate = LocalDateTime.now().plusSeconds(refreshExpiration / 1000);

        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenValue)
                .user(user)
                .expiryDate(expiryDate)
                .revoked(false)
                .build();

        refreshTokenRepository.save(newRefreshToken);
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

        // Mark as revoked in DB
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Add to Redis blacklist with TTL until expiry
        LocalDateTime expiryDate = storedToken.getExpiryDate();
        long secondsUntilExpiry = java.time.Duration.between(LocalDateTime.now(), expiryDate).getSeconds();

        if (secondsUntilExpiry > 0) {
            String blacklistKey = BLACKLIST_PREFIX + refreshTokenValue;
            redisTemplate.opsForValue().set(blacklistKey, "revoked", secondsUntilExpiry, TimeUnit.SECONDS);
        }

        log.info("User logged out, refresh token revoked");
    }

    @Transactional(readOnly = true)
    public UserDto getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));
        return mapToUserDto(user);
    }

    @Transactional
    public UserDto updateProfile(Long userId, String newUsername, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));

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

        User saved = userRepository.save(user);
        log.info("Profile updated for user {}", userId);
        return mapToUserDto(saved);
    }

    private UserDto mapToUserDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
