package com.cinema.hall.repository;

import com.cinema.hall.entity.ExtraService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
// JpaRepository<ExtraService, Long> — Spring Data JPA автоматически реализует стандартные методы CRUD.
public interface ExtraServiceRepository extends JpaRepository<ExtraService, Long> {

    // Spring Data JPA генерирует SQL по имени метода (Query Method Naming Convention):
    //   findBy         → SELECT ... FROM extra_services WHERE
    //   HallId         → hall_id = ?  (Spring автоматически транслирует поле hall.id через @JoinColumn)
    //
    // Генерируемый SQL: SELECT * FROM extra_services WHERE hall_id = ?
    //
    // Используется в:
    //   ExtraServiceService.getExtraServicesByHallId(hallId)
    //   SessionService.getExtraServicesForSession(sessionId) — получает услуги зала для сеанса
    List<ExtraService> findByHallId(Long hallId);
}
