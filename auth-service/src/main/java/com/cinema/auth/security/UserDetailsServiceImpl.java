package com.cinema.auth.security; // Пакет безопасности auth-service

import com.cinema.auth.entity.User;
import com.cinema.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;                                  // @RequiredArgsConstructor: конструктор для final полей
import org.springframework.security.core.authority.SimpleGrantedAuthority; // Роль пользователя в Spring Security
import org.springframework.security.core.userdetails.UserDetails;       // Интерфейс Spring Security для деталей пользователя
import org.springframework.security.core.userdetails.UserDetailsService; // Интерфейс загрузки пользователя по логину
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Исключение если пользователь не найден
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    // Связывает Spring Security с нашей БД пользователей.
    // AuthenticationManager при логине вызывает loadUserByUsername() — и сравнивает пароли через BCrypt.

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username)); // Если не найден — Spring Security вернёт 401

        // Преобразуем нашу User-сущность в UserDetails (Spring Security объект)
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())  // Логин
                .password(user.getPassword())  // BCrypt-хеш — Spring Security сам сравнит с введённым паролем
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().name())))
                // authorities = роль пользователя в Spring Security. SimpleGrantedAuthority("ROLE_ADMIN")
                .build();
    }
}
