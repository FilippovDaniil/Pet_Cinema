package com.cinema.movie.repository;

import com.cinema.movie.entity.Movie;
import com.cinema.movie.entity.MovieType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN m.genres g WHERE " +
           "(:genre IS NULL OR g.name = :genre) AND " +
           "(:type IS NULL OR m.type = :type) AND " +
           "(:durationMax IS NULL OR m.durationMinutes <= :durationMax)")
    Page<Movie> findAllWithFilters(
            @Param("genre") String genre,
            @Param("type") MovieType type,
            @Param("durationMax") Integer durationMax,
            Pageable pageable
    );
}
