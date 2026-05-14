package com.cinema.movie.controller;

import com.cinema.dto.movie.CommentCreateRequest;
import com.cinema.dto.movie.CommentDto;
import com.cinema.dto.movie.MovieCreateRequest;
import com.cinema.dto.movie.MovieDto;
import com.cinema.dto.movie.ReviewCreateRequest;
import com.cinema.dto.movie.ReviewDto;
import com.cinema.movie.config.SecurityConfig;
import com.cinema.movie.dto.PageResponse;
import com.cinema.movie.exception.ResourceNotFoundException;
import com.cinema.movie.security.JwtUtils;
import com.cinema.movie.service.MovieService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest(MovieController.class) — поднимает только Web-слой (не всё приложение):
//   - MovieController (указан явно)
//   - MockMvc (HTTP клиент для тестов без реального сервера)
//   - ObjectMapper (Jackson для сериализации JSON)
//   - НЕ запускает: базу данных, Kafka, Redis, DataLoader
//
// @Import(SecurityConfig.class) — ОБЯЗАТЕЛЬНО: без этого SecurityConfig не загружается
//   (WebMvcTest не сканирует @Configuration классы). JwtAuthFilter тоже подхватывается через SecurityConfig.
//   Это позволяет тестировать реальные правила авторизации (@PreAuthorize и т.д.).
@WebMvcTest(MovieController.class)
@Import(SecurityConfig.class)
@DisplayName("MovieController Web Layer Tests")
class MovieControllerTest {

    @Autowired
    private MockMvc mockMvc; // Выполняет HTTP запросы в тестовом контексте (без сетевого стека)

    @Autowired
    private ObjectMapper objectMapper; // Для сериализации объектов в JSON-тело запроса

    @MockBean
    private MovieService movieService; // Мок сервиса — контроллер вызывает его, мы контролируем ответы

    // JwtUtils мокируется хотя напрямую не используется в тестах.
    // JwtAuthFilter (загружается через SecurityConfig) автовайрит JwtUtils — без мока Spring не сможет
    // создать JwtAuthFilter и весь контекст упадёт с NoSuchBeanDefinitionException.
    @MockBean
    private JwtUtils jwtUtils;

    private PageResponse<MovieDto> samplePage; // Тестовые данные, переиспользуемые в нескольких тестах
    private MovieDto sampleMovie;

    // @BeforeEach: выполняется перед каждым тестом — инициализируем тестовые данные
    @BeforeEach
    void setUp() {
        sampleMovie = MovieDto.builder()
                .id(1L)
                .title("Interstellar")
                .description("Space odyssey")
                .posterUrl("http://poster.com/1.jpg")
                .durationMinutes(169)
                .type("TWO_D")
                .genres(List.of("Drama"))
                .averageRating(4.8)
                .build();

        samplePage = PageResponse.<MovieDto>builder()
                .content(List.of(sampleMovie))
                .page(0)
                .size(10)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // GET /api/movies
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/movies → 200, returns page of movies")
    void getAllMovies_returns200WithPage() throws Exception {
        when(movieService.getAllMovies(isNull(), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(samplePage);

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("Interstellar"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("GET /api/movies?genre=Action → 200, passes genre filter to service")
    void getAllMovies_withGenreFilter_returns200() throws Exception {
        MovieDto actionMovie = MovieDto.builder()
                .id(2L).title("Action Movie").type("THREE_D")
                .genres(List.of("Action")).durationMinutes(100)
                .build();

        PageResponse<MovieDto> actionPage = PageResponse.<MovieDto>builder()
                .content(List.of(actionMovie))
                .page(0).size(10).totalElements(1).totalPages(1).last(true)
                .build();

        when(movieService.getAllMovies(eq("Action"), isNull(), isNull(), eq(0), eq(10)))
                .thenReturn(actionPage);

        mockMvc.perform(get("/api/movies").param("genre", "Action"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Action Movie"))
                .andExpect(jsonPath("$.content[0].genres[0]").value("Action"));
    }

    // ────────────────────────────────────────────────────────────────────────────
    // GET /api/movies/{id}
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/movies/{id} → 200, returns MovieDto")
    void getMovieById_found_returns200() throws Exception {
        when(movieService.getMovieById(1L)).thenReturn(sampleMovie);

        mockMvc.perform(get("/api/movies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Interstellar"))
                .andExpect(jsonPath("$.averageRating").value(4.8));
    }

    @Test
    @DisplayName("GET /api/movies/{id} when not found → 404")
    void getMovieById_notFound_returns404() throws Exception {
        when(movieService.getMovieById(99L))
                .thenThrow(new ResourceNotFoundException("Movie not found with id: 99"));

        mockMvc.perform(get("/api/movies/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ────────────────────────────────────────────────────────────────────────────
    // POST /api/movies
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/movies as ADMIN → 201 Created")
    @WithMockUser(roles = "ADMIN") // Создаёт пользователя с ролью ROLE_ADMIN в SecurityContext
    void createMovie_asAdmin_returns201() throws Exception {
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("New Movie")
                .description("Exciting film")
                .posterUrl("http://poster.com/new.jpg")
                .durationMinutes(100)
                .type("TWO_D")
                .genreIds(List.of(1L))
                .build();

        // Проблема @WithMockUser: principal = String "user", а контроллер делает (Long) authentication.getPrincipal().
        // Решение: используем authentication() с явным Long principal.
        // @WithMockUser остаётся для документации намерения, но authentication() переопределяет SecurityContext.
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        // 1L — Long principal (userId из JWT в реальном приложении)

        when(movieService.createMovie(any(MovieCreateRequest.class), eq(1L)))
                .thenReturn(sampleMovie);

        mockMvc.perform(post("/api/movies")
                        .with(authentication(adminAuth)) // Подменяем SecurityContext нашим adminAuth
                        .with(csrf())                    // csrf() добавляет CSRF-токен (нужен для POST с SecurityConfig)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Interstellar")); // Сервис вернул sampleMovie
    }

    @Test
    @DisplayName("POST /api/movies as CLIENT → 403 Forbidden")
    @WithMockUser(roles = "CLIENT")
    void createMovie_asClient_returns403() throws Exception {
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("Movie")
                .durationMinutes(90)
                .type("TWO_D")
                .genreIds(List.of(1L))
                .build();

        mockMvc.perform(post("/api/movies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/movies unauthenticated → 401 or 403")
    void createMovie_unauthenticated_returns401or403() throws Exception {
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("Movie")
                .durationMinutes(90)
                .type("TWO_D")
                .genreIds(List.of(1L))
                .build();

        mockMvc.perform(post("/api/movies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // PUT /api/movies/{id}
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/movies/{id} as ADMIN → 200 OK")
    void updateMovie_asAdmin_returns200() throws Exception {
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("Updated Movie")
                .description("Updated desc")
                .durationMinutes(130)
                .type("THREE_D")
                .genreIds(List.of(1L))
                .build();

        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        MovieDto updatedDto = MovieDto.builder()
                .id(1L).title("Updated Movie").type("THREE_D")
                .durationMinutes(130).genres(List.of("Action"))
                .build();

        when(movieService.updateMovie(eq(1L), any(MovieCreateRequest.class))).thenReturn(updatedDto);

        mockMvc.perform(put("/api/movies/1")
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Movie"));
    }

    // ────────────────────────────────────────────────────────────────────────────
    // DELETE /api/movies/{id}
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/movies/{id} as ADMIN → 204 No Content")
    void deleteMovie_asAdmin_returns204() throws Exception {
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(delete("/api/movies/1")
                        .with(authentication(adminAuth))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/movies/{id} when not found → 404")
    void deleteMovie_notFound_returns404() throws Exception {
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        doThrow(new ResourceNotFoundException("Movie not found with id: 99"))
                .when(movieService).deleteMovie(99L);

        mockMvc.perform(delete("/api/movies/99")
                        .with(authentication(adminAuth))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // POST /api/movies/{id}/reviews
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/movies/{id}/reviews as CLIENT → 201 Created")
    void addReview_asClient_returns201() throws Exception {
        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .rating(5)
                .comment("Amazing!")
                .build();

        ReviewDto reviewDto = ReviewDto.builder()
                .id(1L)
                .movieId(1L)
                .userId(2L) // userId = 2L (из нашего clientAuth principal)
                .rating(5)
                .comment("Amazing!")
                .createdAt(LocalDateTime.now())
                .build();

        // CLIENT с userId=2L в principal — контроллер передаст 2L в movieService.addReview()
        UsernamePasswordAuthenticationToken clientAuth = new UsernamePasswordAuthenticationToken(
                2L, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));

        // eq(2L): убеждаемся что сервис вызван именно с userId=2L (из токена, не из тела запроса)
        when(movieService.addReview(eq(1L), any(ReviewCreateRequest.class), eq(2L)))
                .thenReturn(reviewDto);

        mockMvc.perform(post("/api/movies/1/reviews")
                        .with(authentication(clientAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())       // HTTP 201
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Amazing!"));
    }

    @Test
    @DisplayName("POST /api/movies/{id}/reviews unauthenticated → 401 or 403")
    void addReview_unauthenticated_returns401or403() throws Exception {
        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .rating(4)
                .comment("Good")
                .build();

        mockMvc.perform(post("/api/movies/1/reviews")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /api/movies/{id}/reviews as ADMIN → 403 (only CLIENT role allowed)")
    void addReview_asAdmin_returns403() throws Exception {
        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .rating(4)
                .comment("Admin trying to review")
                .build();

        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(post("/api/movies/1/reviews")
                        .with(authentication(adminAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // POST /api/movies/{id}/comments
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/movies/{id}/comments as CLIENT → 201 Created")
    void addComment_asClient_returns201() throws Exception {
        CommentCreateRequest req = CommentCreateRequest.builder()
                .text("Loved this film!")
                .build();

        CommentDto commentDto = CommentDto.builder()
                .id(1L)
                .movieId(1L)
                .userId(3L)
                .text("Loved this film!")
                .createdAt(LocalDateTime.now())
                .build();

        UsernamePasswordAuthenticationToken clientAuth = new UsernamePasswordAuthenticationToken(
                3L, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));

        when(movieService.addComment(eq(1L), any(CommentCreateRequest.class), eq(3L)))
                .thenReturn(commentDto);

        mockMvc.perform(post("/api/movies/1/comments")
                        .with(authentication(clientAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Loved this film!"))
                .andExpect(jsonPath("$.userId").value(3));
    }
}
