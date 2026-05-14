package com.cinema.hall.controller;

import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.SessionCreateRequest;
import com.cinema.dto.hall.SessionDto;
import com.cinema.hall.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sessions") // Базовый путь для всех эндпоинтов сеансов
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    // GET /api/sessions?movieId=1&hallId=2&from=2026-06-01 12:00:00&to=2026-06-07 18:00:00
    // Все параметры опциональны (required = false → null если не передан).
    // Публичный: GET /api/sessions/** разрешён всем в SecurityConfig.
    //
    // @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") — указывает Spring как парсить
    // LocalDateTime из строки query-параметра.
    // Без этой аннотации Spring не знает формат и кидает MethodArgumentTypeMismatchException.
    // Пример URL: ?from=2026-06-01 12:00:00 (пробел нужно кодировать как %20 в настоящем URL).
    @GetMapping
    public ResponseEntity<List<SessionDto>> getSessions(
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long hallId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to
    ) {
        return ResponseEntity.ok(sessionService.getSessions(movieId, hallId, from, to));
    }

    // GET /api/sessions/{id} → сеанс по id
    // Публичный
    @GetMapping("/{id}")
    public ResponseEntity<SessionDto> getSessionById(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getSessionById(id));
    }

    // GET /api/sessions/{id}/extra-services → доп.услуги зала сеанса
    // Публичный. Вложенный ресурс — услуги зала, привязанного к данному сеансу.
    // order-service вызывает этот эндпоинт через lb://hall-service чтобы узнать
    // список и цены доп.услуг при оформлении заказа клиентом.
    @GetMapping("/{id}/extra-services")
    public ResponseEntity<List<ExtraServiceDto>> getExtraServicesForSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getExtraServicesForSession(id));
    }

    // POST /api/sessions → создание нового сеанса
    // Только ADMIN
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SessionDto> createSession(@Valid @RequestBody SessionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(request)); // 201
    }

    // PUT /api/sessions/{id} → обновление сеанса (время, зал, цена)
    // Только ADMIN
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SessionDto> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody SessionCreateRequest request
    ) {
        return ResponseEntity.ok(sessionService.updateSession(id, request));
    }

    // DELETE /api/sessions/{id} → мягкое удаление (active = false)
    // Только ADMIN
    // Мягкое удаление — не физическое удаление из БД.
    // Сеанс исчезает из списков API, но данные остаются для билетов.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
