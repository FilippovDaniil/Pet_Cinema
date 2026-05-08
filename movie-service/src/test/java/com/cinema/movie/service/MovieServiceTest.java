package com.cinema.movie.service;

import com.cinema.dto.movie.CommentCreateRequest;
import com.cinema.dto.movie.CommentDto;
import com.cinema.dto.movie.MovieCreateRequest;
import com.cinema.dto.movie.MovieDto;
import com.cinema.dto.movie.ReviewCreateRequest;
import com.cinema.dto.movie.ReviewDto;
import com.cinema.movie.dto.PageResponse;
import com.cinema.movie.entity.Comment;
import com.cinema.movie.entity.Genre;
import com.cinema.movie.entity.Movie;
import com.cinema.movie.entity.MovieType;
import com.cinema.movie.entity.Review;
import com.cinema.movie.exception.AlreadyExistsException;
import com.cinema.movie.exception.ResourceNotFoundException;
import com.cinema.movie.repository.CommentRepository;
import com.cinema.movie.repository.GenreRepository;
import com.cinema.movie.repository.MovieRepository;
import com.cinema.movie.repository.ReviewRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
@DisplayName("MovieService Unit Tests")
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MovieService movieService;

    private static final String CACHE_KEY = "movies:list:all";
    private static final long CACHE_TTL = 600L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(movieService, "moviesCacheKey", CACHE_KEY);
        ReflectionTestUtils.setField(movieService, "moviesCacheTtl", CACHE_TTL);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // getAllMovies tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllMovies: no filters, cache miss → repository called, result cached")
    void getAllMovies_noFilters_cacheMiss() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        Genre action = Genre.builder().id(1L).name("Action").build();
        Movie movie = buildMovie(1L, "Test Movie", action);
        Page<Movie> moviePage = new PageImpl<>(List.of(movie), PageRequest.of(0, 10), 1);

        when(movieRepository.findAllWithFilters(null, null, null, PageRequest.of(0, 10)))
                .thenReturn(moviePage);
        when(reviewRepository.findByMovieId(1L)).thenReturn(Collections.emptyList());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"content\":[]}");

        PageResponse<MovieDto> result = movieService.getAllMovies(null, null, null, 0, 10);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Test Movie");

        verify(movieRepository).findAllWithFilters(null, null, null, PageRequest.of(0, 10));
        verify(valueOperations).set(eq(CACHE_KEY), anyString(), any());
    }

    @Test
    @DisplayName("getAllMovies: no filters, cache hit → repository NOT called, result from cache")
    void getAllMovies_noFilters_cacheHit() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String cachedJson = "{\"content\":[{\"id\":1,\"title\":\"Cached Movie\"}],\"page\":0,\"size\":10,\"totalElements\":1,\"totalPages\":1,\"last\":true}";
        when(valueOperations.get(CACHE_KEY)).thenReturn(cachedJson);

        PageResponse<MovieDto> cachedResponse = PageResponse.<MovieDto>builder()
                .content(List.of(MovieDto.builder().id(1L).title("Cached Movie").build()))
                .page(0).size(10).totalElements(1).totalPages(1).last(true)
                .build();

        when(objectMapper.readValue(eq(cachedJson), any(TypeReference.class))).thenReturn(cachedResponse);

        PageResponse<MovieDto> result = movieService.getAllMovies(null, null, null, 0, 10);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Cached Movie");

        verify(movieRepository, never()).findAllWithFilters(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getAllMovies: with genre filter → cache NOT checked, repository called directly")
    void getAllMovies_withFilters_noCache() {
        Genre action = Genre.builder().id(1L).name("Action").build();
        Movie movie = buildMovie(1L, "Action Movie", action);
        Page<Movie> moviePage = new PageImpl<>(List.of(movie), PageRequest.of(0, 10), 1);

        when(movieRepository.findAllWithFilters(eq("Action"), any(), any(), any()))
                .thenReturn(moviePage);
        when(reviewRepository.findByMovieId(1L)).thenReturn(Collections.emptyList());

        PageResponse<MovieDto> result = movieService.getAllMovies("Action", null, null, 0, 10);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Action Movie");

        verify(redisTemplate, never()).opsForValue();
        verify(movieRepository).findAllWithFilters(eq("Action"), any(), any(), any());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // getMovieById tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMovieById: found → returns MovieDto with avgRating calculated from reviews")
    void getMovieById_found() {
        Genre action = Genre.builder().id(1L).name("Action").build();
        Movie movie = buildMovie(1L, "Star Wars", action);

        Review review1 = Review.builder().id(1L).movieId(1L).userId(1L).rating(4).comment("Good").createdAt(LocalDateTime.now()).build();
        Review review2 = Review.builder().id(2L).movieId(1L).userId(2L).rating(5).comment("Great").createdAt(LocalDateTime.now()).build();

        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(reviewRepository.findByMovieId(1L)).thenReturn(List.of(review1, review2));

        MovieDto result = movieService.getMovieById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Star Wars");
        assertThat(result.getAverageRating()).isEqualTo(4.5);
        assertThat(result.getGenres()).containsExactly("Action");
    }

    @Test
    @DisplayName("getMovieById: no reviews → averageRating is null")
    void getMovieById_noReviews_avgRatingIsNull() {
        Genre action = Genre.builder().id(1L).name("Action").build();
        Movie movie = buildMovie(1L, "Interstellar", action);

        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie));
        when(reviewRepository.findByMovieId(1L)).thenReturn(Collections.emptyList());

        MovieDto result = movieService.getMovieById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getAverageRating()).isNull();
    }

    @Test
    @DisplayName("getMovieById: not found → throws ResourceNotFoundException")
    void getMovieById_notFound() {
        when(movieRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.getMovieById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // createMovie tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createMovie: success → genres resolved, movie saved, cache invalidated, kafka event published")
    void createMovie_success() {
        Genre action = Genre.builder().id(1L).name("Action").build();
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("New Movie")
                .description("A great movie")
                .posterUrl("http://poster.com/image.jpg")
                .durationMinutes(120)
                .type("TWO_D")
                .genreIds(List.of(1L))
                .build();

        Movie savedMovie = buildMovie(10L, "New Movie", action);

        when(genreRepository.findById(1L)).thenReturn(Optional.of(action));
        when(movieRepository.save(any(Movie.class))).thenReturn(savedMovie);

        MovieDto result = movieService.createMovie(req, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("New Movie");
        assertThat(result.getId()).isEqualTo(10L);

        verify(genreRepository).findById(1L);
        verify(movieRepository).save(any(Movie.class));
        verify(redisTemplate).delete(CACHE_KEY);
        verify(kafkaTemplate).send(eq("movie-update"), eq("10"), any());
    }

    @Test
    @DisplayName("createMovie: genre not found → throws ResourceNotFoundException")
    void createMovie_genreNotFound() {
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("Movie")
                .durationMinutes(90)
                .type("TWO_D")
                .genreIds(List.of(999L))
                .build();

        when(genreRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.createMovie(req, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");

        verify(movieRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // updateMovie tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateMovie: success → updates all fields, saves, invalidates cache, publishes event")
    void updateMovie_success() {
        Genre action = Genre.builder().id(1L).name("Action").build();
        Genre drama = Genre.builder().id(2L).name("Drama").build();

        Movie existing = buildMovie(5L, "Old Title", action);

        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("Updated Title")
                .description("Updated description")
                .posterUrl("http://new-poster.com/img.jpg")
                .durationMinutes(150)
                .type("THREE_D")
                .genreIds(List.of(2L))
                .build();

        Movie updatedMovie = Movie.builder()
                .id(5L)
                .title("Updated Title")
                .description("Updated description")
                .posterUrl("http://new-poster.com/img.jpg")
                .durationMinutes(150)
                .type(MovieType.THREE_D)
                .genres(List.of(drama))
                .build();

        when(movieRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(genreRepository.findById(2L)).thenReturn(Optional.of(drama));
        when(movieRepository.save(any(Movie.class))).thenReturn(updatedMovie);
        when(reviewRepository.findByMovieId(5L)).thenReturn(Collections.emptyList());

        MovieDto result = movieService.updateMovie(5L, req);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Updated Title");
        assertThat(result.getDurationMinutes()).isEqualTo(150);
        assertThat(result.getType()).isEqualTo("THREE_D");

        verify(movieRepository).save(any(Movie.class));
        verify(redisTemplate).delete(CACHE_KEY);
        verify(kafkaTemplate).send(eq("movie-update"), eq("5"), any());
    }

    @Test
    @DisplayName("updateMovie: not found → throws ResourceNotFoundException")
    void updateMovie_notFound() {
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("Title")
                .durationMinutes(90)
                .type("TWO_D")
                .genreIds(List.of())
                .build();

        when(movieRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.updateMovie(42L, req))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("42");

        verify(movieRepository, never()).save(any());
        verify(redisTemplate, never()).delete(anyString());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // deleteMovie tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteMovie: success → deletes, invalidates cache, publishes DELETED event")
    void deleteMovie_success() {
        Genre action = Genre.builder().id(1L).name("Action").build();
        Movie movie = buildMovie(7L, "Movie To Delete", action);

        when(movieRepository.findById(7L)).thenReturn(Optional.of(movie));

        movieService.deleteMovie(7L);

        verify(movieRepository).delete(movie);
        verify(redisTemplate).delete(CACHE_KEY);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("movie-update"), eq("7"), eventCaptor.capture());

        Object sentEvent = eventCaptor.getValue();
        assertThat(sentEvent).hasFieldOrPropertyWithValue("action", "DELETED");
        assertThat(sentEvent).hasFieldOrPropertyWithValue("movieId", 7L);
        assertThat(sentEvent).hasFieldOrPropertyWithValue("title", "Movie To Delete");
    }

    @Test
    @DisplayName("deleteMovie: not found → throws ResourceNotFoundException")
    void deleteMovie_notFound() {
        when(movieRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.deleteMovie(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(movieRepository, never()).delete(any());
        verify(redisTemplate, never()).delete(anyString());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // addReview tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addReview: success → movie exists, not already reviewed, saves and returns ReviewDto")
    void addReview_success() {
        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .rating(4)
                .comment("Very good!")
                .build();

        Review savedReview = Review.builder()
                .id(1L)
                .movieId(3L)
                .userId(10L)
                .rating(4)
                .comment("Very good!")
                .createdAt(LocalDateTime.now())
                .build();

        when(movieRepository.existsById(3L)).thenReturn(true);
        when(reviewRepository.existsByMovieIdAndUserId(3L, 10L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        ReviewDto result = movieService.addReview(3L, req, 10L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getMovieId()).isEqualTo(3L);
        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getRating()).isEqualTo(4);
        assertThat(result.getComment()).isEqualTo("Very good!");

        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("addReview: already reviewed by same user → throws AlreadyExistsException")
    void addReview_alreadyReviewed() {
        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .rating(3)
                .comment("Duplicate")
                .build();

        when(movieRepository.existsById(3L)).thenReturn(true);
        when(reviewRepository.existsByMovieIdAndUserId(3L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> movieService.addReview(3L, req, 10L))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessageContaining("already reviewed");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("addReview: movie not found → throws ResourceNotFoundException")
    void addReview_movieNotFound() {
        ReviewCreateRequest req = ReviewCreateRequest.builder()
                .rating(5)
                .comment("Excellent")
                .build();

        when(movieRepository.existsById(55L)).thenReturn(false);

        assertThatThrownBy(() -> movieService.addReview(55L, req, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("55");

        verify(reviewRepository, never()).existsByMovieIdAndUserId(anyLong(), anyLong());
        verify(reviewRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // addComment tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addComment: success → saves Comment and returns CommentDto with correct fields")
    void addComment_success() {
        CommentCreateRequest req = CommentCreateRequest.builder()
                .text("Great movie!")
                .build();

        Comment savedComment = Comment.builder()
                .id(1L)
                .movieId(4L)
                .userId(20L)
                .text("Great movie!")
                .createdAt(LocalDateTime.now())
                .build();

        when(movieRepository.existsById(4L)).thenReturn(true);
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);

        CommentDto result = movieService.addComment(4L, req, 20L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getMovieId()).isEqualTo(4L);
        assertThat(result.getUserId()).isEqualTo(20L);
        assertThat(result.getText()).isEqualTo("Great movie!");
        assertThat(result.getCreatedAt()).isNotNull();

        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("addComment: movie not found → throws ResourceNotFoundException")
    void addComment_movieNotFound() {
        CommentCreateRequest req = CommentCreateRequest.builder()
                .text("Would comment if movie existed")
                .build();

        when(movieRepository.existsById(77L)).thenReturn(false);

        assertThatThrownBy(() -> movieService.addComment(77L, req, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("77");

        verify(commentRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Helper methods
    // ────────────────────────────────────────────────────────────────────────────

    private Movie buildMovie(Long id, String title, Genre genre) {
        return Movie.builder()
                .id(id)
                .title(title)
                .description("Description of " + title)
                .posterUrl("http://poster.com/" + id + ".jpg")
                .durationMinutes(120)
                .type(MovieType.TWO_D)
                .genres(List.of(genre))
                .build();
    }
}
