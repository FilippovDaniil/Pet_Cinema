package com.cinema.movie;

import com.cinema.dto.movie.CommentCreateRequest;
import com.cinema.dto.movie.CommentDto;
import com.cinema.dto.movie.MovieCreateRequest;
import com.cinema.dto.movie.MovieDto;
import com.cinema.dto.movie.ReviewCreateRequest;
import com.cinema.dto.movie.ReviewDto;
import com.cinema.movie.entity.Genre;
import com.cinema.movie.exception.AlreadyExistsException;
import com.cinema.movie.exception.ResourceNotFoundException;
import com.cinema.movie.repository.GenreRepository;
import com.cinema.movie.repository.MovieRepository;
import com.cinema.movie.repository.ReviewRepository;
import com.cinema.movie.service.MovieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
        }
)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("MovieService Integration Tests")
class MovieServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("movie_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private MovieService movieService;

    @Autowired
    private GenreRepository genreRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    // Mock Redis to avoid needing a real Redis instance
    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    // Mock Kafka producer since KafkaAutoConfiguration is excluded
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Genre savedGenre;

    @BeforeEach
    void setUp() {
        // Clean up data before each test (respect FK order)
        reviewRepository.deleteAll();
        movieRepository.deleteAll();
        genreRepository.deleteAll();

        // Pre-create a Genre for use in tests
        savedGenre = genreRepository.save(Genre.builder().name("Action").build());

        // Mock Redis operations: always return null (cache miss) for opsForValue().get()
        // and do nothing for set() and delete()
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Full flow: create → get
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createAndGetMovie_fullFlow: create genre → create movie → getMovieById verifies all fields")
    void createAndGetMovie_fullFlow() {
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("Inception")
                .description("Dream within a dream")
                .posterUrl("http://poster.com/inception.jpg")
                .durationMinutes(148)
                .type("TWO_D")
                .genreIds(List.of(savedGenre.getId()))
                .build();

        MovieDto created = movieService.createMovie(req, 1L);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getTitle()).isEqualTo("Inception");
        assertThat(created.getDescription()).isEqualTo("Dream within a dream");
        assertThat(created.getPosterUrl()).isEqualTo("http://poster.com/inception.jpg");
        assertThat(created.getDurationMinutes()).isEqualTo(148);
        assertThat(created.getType()).isEqualTo("TWO_D");
        assertThat(created.getGenres()).containsExactly("Action");

        MovieDto fetched = movieService.getMovieById(created.getId());

        assertThat(fetched).isNotNull();
        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getTitle()).isEqualTo("Inception");
        assertThat(fetched.getGenres()).containsExactly("Action");
        assertThat(fetched.getAverageRating()).isNull(); // no reviews yet
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Review flow: add review, verify rating
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addReview_andVerifyRating: create movie → add review with rating 4 → avgRating = 4.0")
    void addReview_andVerifyRating() {
        MovieCreateRequest movieReq = MovieCreateRequest.builder()
                .title("The Matrix")
                .description("Red pill or blue pill?")
                .durationMinutes(136)
                .type("THREE_D")
                .genreIds(List.of(savedGenre.getId()))
                .build();

        MovieDto movie = movieService.createMovie(movieReq, 1L);

        ReviewCreateRequest reviewReq = ReviewCreateRequest.builder()
                .rating(4)
                .comment("A mind-bending experience")
                .build();

        ReviewDto review = movieService.addReview(movie.getId(), reviewReq, 10L);

        assertThat(review).isNotNull();
        assertThat(review.getMovieId()).isEqualTo(movie.getId());
        assertThat(review.getUserId()).isEqualTo(10L);
        assertThat(review.getRating()).isEqualTo(4);
        assertThat(review.getComment()).isEqualTo("A mind-bending experience");

        MovieDto fetched = movieService.getMovieById(movie.getId());
        assertThat(fetched.getAverageRating()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("addReview_multipleReviews_avgRatingCalculatedCorrectly: ratings [3, 5] → avgRating = 4.0")
    void addReview_multipleReviews_avgRatingCalculatedCorrectly() {
        MovieCreateRequest movieReq = MovieCreateRequest.builder()
                .title("Avatar")
                .description("World of Pandora")
                .durationMinutes(162)
                .type("FIVE_D")
                .genreIds(List.of(savedGenre.getId()))
                .build();

        MovieDto movie = movieService.createMovie(movieReq, 1L);

        movieService.addReview(movie.getId(),
                ReviewCreateRequest.builder().rating(3).comment("OK").build(), 11L);
        movieService.addReview(movie.getId(),
                ReviewCreateRequest.builder().rating(5).comment("Great!").build(), 12L);

        MovieDto fetched = movieService.getMovieById(movie.getId());
        assertThat(fetched.getAverageRating()).isEqualTo(4.0);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Double review guard
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addReview_twice_throwsAlreadyExists: second review by same user → throws AlreadyExistsException")
    void addReview_twice_throwsAlreadyExists() {
        MovieCreateRequest movieReq = MovieCreateRequest.builder()
                .title("Dune")
                .description("The spice must flow")
                .durationMinutes(155)
                .type("TWO_D")
                .genreIds(List.of(savedGenre.getId()))
                .build();

        MovieDto movie = movieService.createMovie(movieReq, 1L);

        ReviewCreateRequest firstReview = ReviewCreateRequest.builder()
                .rating(5)
                .comment("Masterpiece")
                .build();

        ReviewCreateRequest secondReview = ReviewCreateRequest.builder()
                .rating(3)
                .comment("Reconsidering...")
                .build();

        movieService.addReview(movie.getId(), firstReview, 20L);

        assertThatThrownBy(() -> movieService.addReview(movie.getId(), secondReview, 20L))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessageContaining("already reviewed");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Delete flow
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteMovie_thenGet_throwsNotFound: create, delete, then getById → throws ResourceNotFoundException")
    void deleteMovie_thenGet_throwsNotFound() {
        MovieCreateRequest movieReq = MovieCreateRequest.builder()
                .title("Ephemeral Film")
                .description("It will be gone soon")
                .durationMinutes(90)
                .type("TWO_D")
                .genreIds(List.of(savedGenre.getId()))
                .build();

        MovieDto movie = movieService.createMovie(movieReq, 1L);
        Long movieId = movie.getId();

        assertThat(movieService.getMovieById(movieId)).isNotNull();

        movieService.deleteMovie(movieId);

        assertThatThrownBy(() -> movieService.getMovieById(movieId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(movieId.toString());
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Update flow
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateMovie_success: create movie → update title and type → getById reflects changes")
    void updateMovie_success() {
        MovieCreateRequest createReq = MovieCreateRequest.builder()
                .title("Original Title")
                .description("Original description")
                .durationMinutes(100)
                .type("TWO_D")
                .genreIds(List.of(savedGenre.getId()))
                .build();

        MovieDto created = movieService.createMovie(createReq, 1L);

        Genre drama = genreRepository.save(Genre.builder().name("Drama").build());

        MovieCreateRequest updateReq = MovieCreateRequest.builder()
                .title("Updated Title")
                .description("Updated description")
                .posterUrl("http://new-poster.com/img.jpg")
                .durationMinutes(130)
                .type("THREE_D")
                .genreIds(List.of(drama.getId()))
                .build();

        MovieDto updated = movieService.updateMovie(created.getId(), updateReq);

        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getDurationMinutes()).isEqualTo(130);
        assertThat(updated.getType()).isEqualTo("THREE_D");
        assertThat(updated.getGenres()).containsExactly("Drama");

        MovieDto fetched = movieService.getMovieById(created.getId());
        assertThat(fetched.getTitle()).isEqualTo("Updated Title");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Comment flow
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addComment_success: create movie → add comment → CommentDto returned with correct fields")
    void addComment_success() {
        MovieCreateRequest movieReq = MovieCreateRequest.builder()
                .title("Commented Movie")
                .description("Ready for comments")
                .durationMinutes(110)
                .type("TWO_D")
                .genreIds(List.of(savedGenre.getId()))
                .build();

        MovieDto movie = movieService.createMovie(movieReq, 1L);

        CommentCreateRequest commentReq = CommentCreateRequest.builder()
                .text("This film changed my life!")
                .build();

        CommentDto comment = movieService.addComment(movie.getId(), commentReq, 30L);

        assertThat(comment).isNotNull();
        assertThat(comment.getMovieId()).isEqualTo(movie.getId());
        assertThat(comment.getUserId()).isEqualTo(30L);
        assertThat(comment.getText()).isEqualTo("This film changed my life!");
        assertThat(comment.getCreatedAt()).isNotNull();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Genre not found on createMovie
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createMovie_unknownGenre_throwsResourceNotFoundException")
    void createMovie_unknownGenre_throwsResourceNotFoundException() {
        MovieCreateRequest req = MovieCreateRequest.builder()
                .title("Movie With Missing Genre")
                .durationMinutes(90)
                .type("TWO_D")
                .genreIds(List.of(9999L))
                .build();

        assertThatThrownBy(() -> movieService.createMovie(req, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("9999");
    }
}
