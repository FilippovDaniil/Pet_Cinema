package com.cinema.hall.controller;

import com.cinema.dto.hall.ExtraServiceCreateRequest;
import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.HallCreateRequest;
import com.cinema.dto.hall.HallDto;
import com.cinema.hall.service.ExtraServiceService;
import com.cinema.hall.service.HallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController = @Controller + @ResponseBody:
//   @Controller — компонент Spring MVC, обрабатывает HTTP-запросы
//   @ResponseBody — все методы возвращают JSON (через Jackson), не View
@RestController
// @RequestMapping — базовый путь для всех эндпоинтов контроллера
@RequestMapping("/api/halls")
@RequiredArgsConstructor
public class HallController {

    private final HallService hallService;
    private final ExtraServiceService extraServiceService;

    // GET /api/halls → список всех залов
    // Публичный: разрешён в SecurityConfig (.requestMatchers(HttpMethod.GET, "/api/halls/**").permitAll())
    @GetMapping
    public ResponseEntity<List<HallDto>> getAllHalls() {
        return ResponseEntity.ok(hallService.getAllHalls()); // 200 OK + JSON список
    }

    // GET /api/halls/{id} → данные конкретного зала по id
    // Публичный (GET /api/halls/** разрешён всем)
    // @PathVariable — извлекает {id} из URL пути (не query-параметр)
    @GetMapping("/{id}")
    public ResponseEntity<HallDto> getHallById(@PathVariable Long id) {
        return ResponseEntity.ok(hallService.getHallById(id)); // 200 OK или 404 если не найден
    }

    // POST /api/halls → создание нового зала
    // @PreAuthorize — метод-уровневая авторизация (работает благодаря @EnableMethodSecurity в SecurityConfig).
    // Только ROLE_ADMIN может создавать залы.
    // hasAuthority() проверяет точное значение; hasRole() добавляет префикс "ROLE_" (не используем).
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<HallDto> createHall(@Valid @RequestBody HallCreateRequest request) {
        // @Valid — запускает Bean Validation на полях HallCreateRequest (@NotBlank, @Positive и т.д.)
        // При нарушении кидается MethodArgumentNotValidException → 400 из GlobalExceptionHandler
        // @RequestBody — десериализует JSON тело запроса в HallCreateRequest через Jackson
        return ResponseEntity.status(HttpStatus.CREATED).body(hallService.createHall(request)); // 201 Created
    }

    // PUT /api/halls/{id} → полное обновление зала (все поля замены)
    // Только ADMIN
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<HallDto> updateHall(
            @PathVariable Long id,           // ID зала из URL
            @Valid @RequestBody HallCreateRequest request // Новые данные зала
    ) {
        return ResponseEntity.ok(hallService.updateHall(id, request)); // 200 OK
    }

    // DELETE /api/halls/{id} → удаление зала
    // Только ADMIN
    // ResponseEntity<Void> — тело ответа пустое (нет смысла возвращать удалённый зал)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteHall(@PathVariable Long id) {
        hallService.deleteHall(id);
        return ResponseEntity.noContent().build(); // 204 No Content (успешное удаление без тела)
    }

    // GET /api/halls/{id}/extra-services → список доп.услуг зала
    // Публичный: входит в /api/halls/** GET маршрут
    // Полный путь: /api/halls/{id}/extra-services (вложенный ресурс)
    @GetMapping("/{id}/extra-services")
    public ResponseEntity<List<ExtraServiceDto>> getExtraServices(@PathVariable Long id) {
        return ResponseEntity.ok(extraServiceService.getExtraServicesByHallId(id));
    }

    // POST /api/halls/{id}/extra-services → добавление доп.услуги к залу
    // Только ADMIN
    @PostMapping("/{id}/extra-services")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ExtraServiceDto> addExtraService(
            @PathVariable Long id,                             // ID зала
            @Valid @RequestBody ExtraServiceCreateRequest request // Данные новой услуги
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(extraServiceService.addExtraService(id, request)); // 201
    }
}
