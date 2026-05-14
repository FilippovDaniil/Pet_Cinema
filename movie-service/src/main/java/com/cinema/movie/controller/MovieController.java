package com.cinema.movie.controller;

import com.cinema.dto.movie.*;
import com.cinema.movie.dto.PageResponse;
import com.cinema.movie.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Главный контроллер movie-service: обрабатывает CRUD фильмов, отзывы и комментарии.
// @RequestMapping("/api/movies") — все маршруты начинаются с /api/movies.
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    // GET /api/movies — публичный, поддерживает фильтры и пагинацию.
    // required = false: параметры необязательны; если не переданы — null (или defaultValue).
    // Примеры:
    //   GET /api/movies                          → все фильмы, страница 0, 10 штук
    //   GET /api/movies?genre=Action&type=THREE_D → фильтр по жанру и типу
    //   GET /api/movies?page=1&size=5            → вторая страница по 5 элементов
    @GetMapping
    public ResponseEntity<PageResponse<MovieDto>> getAllMovies(
            @RequestParam(required = false) String genre,           // фильтр по жанру (name)
            @RequestParam(required = false) String type,            // фильтр по типу ("TWO_D", "THREE_D", "FIVE_D")
            @RequestParam(required = false) Integer durationMax,    // фильтр: фильмы не длиннее X минут
            @RequestParam(defaultValue = "0") int page,            // номер страницы (0-based)
            @RequestParam(defaultValue = "10") int size            // размер страницы
    ) {
        return ResponseEntity.ok(movieService.getAllMovies(genre, type, durationMax, page, size));
    }

    // GET /api/movies/{id} — публичный, получение одного фильма с отзывами и средним рейтингом.
    // @PathVariable Long id: Spring конвертирует строку из URL в Long автоматически.
    @GetMapping("/{id}")
    public ResponseEntity<MovieDto> getMovieById(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.getMovieById(id));
    }

    // POST /api/movies — только ADMIN.
    // Authentication authentication: Spring автоматически инжектирует из SecurityContext.
    // authentication.getPrincipal() возвращает principal, установленный в JwtAuthFilter —
    // это Long userId (мы устанавливали: new UsernamePasswordAuthenticationToken(userId, ...)).
    // Каст (Long) безопасен, так как JwtAuthFilter всегда кладёт Long.
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MovieDto> createMovie(
            @Valid @RequestBody MovieCreateRequest request,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal(); // Кто создаёт фильм
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.createMovie(request, userId));
    }

    // PUT /api/movies/{id} — полное обновление фильма, только ADMIN.
    // PUT (не PATCH): клиент передаёт ВСЕ поля, мы перезаписываем их все.
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MovieDto> updateMovie(
            @PathVariable Long id,
            @Valid @RequestBody MovieCreateRequest request
    ) {
        return ResponseEntity.ok(movieService.updateMovie(id, request));
    }

    // DELETE /api/movies/{id} — удаление, только ADMIN.
    // ResponseEntity<Void>: тело ответа пустое (Void = null).
    // noContent().build() → HTTP 204 No Content (стандарт для DELETE).
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteMovie(@PathVariable Long id) {
        movieService.deleteMovie(id);
        return ResponseEntity.noContent().build(); // HTTP 204: операция выполнена, тела нет
    }

    // POST /api/movies/{id}/reviews — добавить отзыв, только ROLE_CLIENT.
    // userId берётся из JWT-токена (не из тела запроса) — нельзя оставить отзыв от чужого имени.
    @PostMapping("/{id}/reviews")
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public ResponseEntity<ReviewDto> addReview(
            @PathVariable Long id,                    // id фильма
            @Valid @RequestBody ReviewCreateRequest request,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal(); // Идентификатор из токена
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.addReview(id, request, userId));
    }

    // POST /api/movies/{id}/comments — добавить комментарий, только ROLE_CLIENT.
    // В отличие от reviews: можно оставить несколько комментариев от одного пользователя.
    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('ROLE_CLIENT')")
    public ResponseEntity<CommentDto> addComment(
            @PathVariable Long id,
            @Valid @RequestBody CommentCreateRequest request,
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.addComment(id, request, userId));
    }
}
