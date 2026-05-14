package com.cinema.auth.config; // Пакет конфигураций auth-service

import com.cinema.auth.entity.Role;
import com.cinema.auth.entity.User;
import com.cinema.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner; // Интерфейс: run() вызывается ПОСЛЕ запуска Spring контекста
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
    // CommandLineRunner.run() выполняется один раз при старте приложения (после инициализации всех бинов).
    // Создаёт тестовых пользователей, если они ещё не существуют.
    // Идемпотентен: повторный запуск не создаст дубликаты (проверяется existsByUsername).

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder из SecurityConfig

    @Override
    public void run(String... args) {
        // Создаём по одному пользователю каждой роли:
        createUserIfNotExists("client1", "client1@cinema.com", "password", Role.ROLE_CLIENT);
        createUserIfNotExists("seller1", "seller1@cinema.com", "password", Role.ROLE_SELLER);
        createUserIfNotExists("admin1",  "admin1@cinema.com",  "password", Role.ROLE_ADMIN);
    }

    private void createUserIfNotExists(String username, String email, String rawPassword, Role role) {
        if (!userRepository.existsByUsername(username)) {
            // Пользователь не существует — создаём
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword)) // Хешируем "password" → BCrypt-хеш
                    .role(role)
                    .build();
            userRepository.save(user); // INSERT INTO users ...
            log.info("Created test user: {} with role: {}", username, role);
        } else {
            log.debug("Test user '{}' already exists, skipping creation", username);
            // DEBUG уровень — не засоряем логи при каждом рестарте
        }
    }
}
