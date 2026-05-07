package com.cinema.hall.repository;

import com.cinema.hall.entity.ExtraService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtraServiceRepository extends JpaRepository<ExtraService, Long> {

    List<ExtraService> findByHallId(Long hallId);
}
