package com.cinema.movie.controller;

import com.cinema.dto.movie.GenreDto;
import com.cinema.movie.service.GenreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController = @Controller + @ResponseBody: все методы возвращают JSON (не HTML-страницу).
// @RequestMapping("/api/genres") — базовый путь для всех эндпоинтов этого контроллера.
// @RequiredArgsConstructor — Lombok: инъекция genreService через конструктор.
@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    // GET /api/genres — публичный эндпоинт (SecurityConfig: .permitAll()).
    // Возвращает список всех жанров для отображения в фильтре на главной странице.
    // HTTP 200 OK автоматически устанавливается ResponseEntity.ok().
    @GetMapping
    public ResponseEntity<List<GenreDto>> getAllGenres() {
        return ResponseEntity.ok(genreService.getAllGenres());
    }

    // POST /api/genres — только для ROLE_ADMIN.
    // @PreAuthorize("hasAuthority('ROLE_ADMIN')") — проверка роли ДО вызова метода.
    // Если роль не совпадает → AccessDeniedException → GlobalExceptionHandler → HTTP 403.
    // @Valid — включает Bean Validation на GenreDto (например @NotBlank на поле name).
    // @RequestBody — десериализует JSON тело запроса в GenreDto.
    // ResponseEntity.status(CREATED) — возвращает HTTP 201 (ресурс создан), а не 200.
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<GenreDto> createGenre(@Valid @RequestBody GenreDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(genreService.createGenre(request));
    }
}
