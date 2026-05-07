package com.cinema.hall.repository;

import com.cinema.hall.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByMovieIdAndActive(Long movieId, boolean active);

    List<Session> findByHallIdAndActive(Long hallId, boolean active);

    @Query("SELECT s FROM Session s WHERE " +
           "(:movieId IS NULL OR s.movieId = :movieId) AND " +
           "(:hallId IS NULL OR s.hall.id = :hallId) AND " +
           "(:from IS NULL OR s.startTime >= :from) AND " +
           "(:to IS NULL OR s.startTime <= :to)")
    List<Session> findByFilters(
            @Param("movieId") Long movieId,
            @Param("hallId") Long hallId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
