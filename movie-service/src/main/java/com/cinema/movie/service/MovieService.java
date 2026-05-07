package com.cinema.movie.service;

import com.cinema.dto.movie.*;
import com.cinema.movie.dto.MovieUpdateEvent;
import com.cinema.movie.dto.PageResponse;
import com.cinema.movie.entity.*;
import com.cinema.movie.exception.AlreadyExistsException;
import com.cinema.movie.exception.ResourceNotFoundException;
import com.cinema.movie.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final ReviewRepository reviewRepository;
    private final CommentRepository commentRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${redis.movies-cache-key}")
    private String moviesCacheKey;

    @Value("${redis.movies-cache-ttl}")
    private long moviesCacheTtl;

    @Transactional(readOnly = true)
    public PageResponse<MovieDto> getAllMovies(String genre, String type, Integer durationMax, int page, int size) {
        // Only use cache when no filters are applied
        if (genre == null && type == null && durationMax == null && page == 0 && size == 10) {
            String cached = redisTemplate.opsForValue().get(moviesCacheKey);
            if (cached != null) {
                try {
                    return objectMapper.readValue(cached, new TypeReference<PageResponse<MovieDto>>() {});
                } catch (Exception e) {
                    log.warn("Failed to deserialize movies cache, fetching from DB", e);
                }
            }
        }

        MovieType movieType = null;
        if (type != null) {
            try {
                movieType = MovieType.valueOf(type);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid movie type filter: {}", type);
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Movie> moviePage = movieRepository.findAllWithFilters(genre, movieType, durationMax, pageable);

        List<MovieDto> dtos = moviePage.getContent().stream()
                .map(m -> toDto(m, reviewRepository.findByMovieId(m.getId())))
                .collect(Collectors.toList());

        PageResponse<MovieDto> response = PageResponse.<MovieDto>builder()
                .content(dtos)
                .page(moviePage.getNumber())
                .size(moviePage.getSize())
                .totalElements(moviePage.getTotalElements())
                .totalPages(moviePage.getTotalPages())
                .last(moviePage.isLast())
                .build();

        if (genre == null && type == null && durationMax == null && page == 0 && size == 10) {
            try {
                String json = objectMapper.writeValueAsString(response);
                redisTemplate.opsForValue().set(moviesCacheKey, json, Duration.ofSeconds(moviesCacheTtl));
            } catch (Exception e) {
                log.warn("Failed to cache movies list", e);
            }
        }

        return response;
    }

    @Transactional(readOnly = true)
    public MovieDto getMovieById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
        List<Review> reviews = reviewRepository.findByMovieId(id);
        return toDto(movie, reviews);
    }

    @Transactional
    public MovieDto createMovie(MovieCreateRequest req, Long userId) {
        List<Genre> genres = resolveGenres(req.getGenreIds());
        MovieType type = MovieType.valueOf(req.getType());

        Movie movie = Movie.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .posterUrl(req.getPosterUrl())
                .durationMinutes(req.getDurationMinutes())
                .type(type)
                .genres(genres)
                .build();

        Movie saved = movieRepository.save(movie);
        invalidateCache();
        publishEvent(saved.getId(), "CREATED", saved.getTitle());
        return toDto(saved, List.of());
    }

    @Transactional
    public MovieDto updateMovie(Long id, MovieCreateRequest req) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));

        movie.setTitle(req.getTitle());
        movie.setDescription(req.getDescription());
        movie.setPosterUrl(req.getPosterUrl());
        movie.setDurationMinutes(req.getDurationMinutes());
        movie.setType(MovieType.valueOf(req.getType()));

        if (req.getGenreIds() != null) {
            movie.setGenres(resolveGenres(req.getGenreIds()));
        }

        Movie updated = movieRepository.save(movie);
        invalidateCache();
        publishEvent(updated.getId(), "UPDATED", updated.getTitle());
        return toDto(updated, reviewRepository.findByMovieId(updated.getId()));
    }

    @Transactional
    public void deleteMovie(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with id: " + id));
        movieRepository.delete(movie);
        invalidateCache();
        publishEvent(id, "DELETED", movie.getTitle());
    }

    @Transactional
    public ReviewDto addReview(Long movieId, ReviewCreateRequest req, Long userId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResourceNotFoundException("Movie not found with id: " + movieId);
        }
        if (reviewRepository.existsByMovieIdAndUserId(movieId, userId)) {
            throw new AlreadyExistsException("User has already reviewed this movie");
        }

        Review review = Review.builder()
                .movieId(movieId)
                .userId(userId)
                .rating(req.getRating())
                .comment(req.getComment())
                .build();

        Review saved = reviewRepository.save(review);
        return toReviewDto(saved);
    }

    @Transactional
    public CommentDto addComment(Long movieId, CommentCreateRequest req, Long userId) {
        if (!movieRepository.existsById(movieId)) {
            throw new ResourceNotFoundException("Movie not found with id: " + movieId);
        }

        Comment comment = Comment.builder()
                .movieId(movieId)
                .userId(userId)
                .text(req.getText())
                .build();

        Comment saved = commentRepository.save(comment);
        return toCommentDto(saved);
    }

    private List<Genre> resolveGenres(List<Long> genreIds) {
        return genreIds.stream()
                .map(gid -> genreRepository.findById(gid)
                        .orElseThrow(() -> new ResourceNotFoundException("Genre not found with id: " + gid)))
                .collect(Collectors.toList());
    }

    private void invalidateCache() {
        redisTemplate.delete(moviesCacheKey);
    }

    private void publishEvent(Long movieId, String action, String title) {
        try {
            MovieUpdateEvent event = MovieUpdateEvent.builder()
                    .movieId(movieId)
                    .action(action)
                    .title(title)
                    .build();
            kafkaTemplate.send("movie-update", String.valueOf(movieId), event);
        } catch (Exception e) {
            log.warn("Failed to publish movie update event for movieId={}", movieId, e);
        }
    }

    private MovieDto toDto(Movie movie, List<Review> reviews) {
        double avgRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        List<String> genreNames = movie.getGenres().stream()
                .map(Genre::getName)
                .collect(Collectors.toList());

        return MovieDto.builder()
                .id(movie.getId())
                .title(movie.getTitle())
                .description(movie.getDescription())
                .posterUrl(movie.getPosterUrl())
                .durationMinutes(movie.getDurationMinutes())
                .type(movie.getType().name())
                .genres(genreNames)
                .averageRating(reviews.isEmpty() ? null : avgRating)
                .build();
    }

    private ReviewDto toReviewDto(Review review) {
        return ReviewDto.builder()
                .id(review.getId())
                .movieId(review.getMovieId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    private CommentDto toCommentDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .movieId(comment.getMovieId())
                .userId(comment.getUserId())
                .text(comment.getText())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
