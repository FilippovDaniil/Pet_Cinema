package com.cinema.movie.repository;

import com.cinema.movie.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMovieId(Long movieId);

    boolean existsByMovieIdAndUserId(Long movieId, Long userId);
}
