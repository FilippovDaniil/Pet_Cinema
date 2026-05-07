package com.cinema.auth.config;

import com.cinema.auth.entity.Role;
import com.cinema.auth.entity.User;
import com.cinema.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createUserIfNotExists("client1", "client1@cinema.com", "password", Role.ROLE_CLIENT);
        createUserIfNotExists("seller1", "seller1@cinema.com", "password", Role.ROLE_SELLER);
        createUserIfNotExists("admin1", "admin1@cinema.com", "password", Role.ROLE_ADMIN);
    }

    private void createUserIfNotExists(String username, String email, String rawPassword, Role role) {
        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .build();
            userRepository.save(user);
            log.info("Created test user: {} with role: {}", username, role);
        } else {
            log.debug("Test user '{}' already exists, skipping creation", username);
        }
    }
}
