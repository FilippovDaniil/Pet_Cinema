package com.cinema.order.controller;

import com.cinema.dto.order.FoodItemDto;
import com.cinema.order.service.FoodMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController — @Controller + @ResponseBody: автоматическая сериализация в JSON
@RestController
// Базовый URL — SecurityConfig делает GET /api/food-menu публичным (permitAll)
@RequestMapping("/api/food-menu")
// @RequiredArgsConstructor — Lombok: конструктор для final поля foodMenuService
@RequiredArgsConstructor
public class FoodMenuController {

    // Сервис управления меню
    private final FoodMenuService foodMenuService;

    // ------------------------------------------------------------------ GET /api/food-menu

    // Публичный эндпоинт — меню доступно всем (разрешено в SecurityConfig без токена)
    @GetMapping
    public ResponseEntity<List<FoodItemDto>> getAllFoodItems() {
        return ResponseEntity.ok(foodMenuService.getAllFoodItems());
    }

    // ------------------------------------------------------------------ POST /api/food-menu

    // Добавление позиции в меню — только ADMIN
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<FoodItemDto> addFoodItem(@Valid @RequestBody FoodItemDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodMenuService.addFoodItem(dto));
    }

    // ------------------------------------------------------------------ PUT /api/food-menu/{id}

    // Обновление позиции меню — только ADMIN
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<FoodItemDto> updateFoodItem(
            @PathVariable Long id,
            @Valid @RequestBody FoodItemDto dto) {
        return ResponseEntity.ok(foodMenuService.updateFoodItem(id, dto));
    }

    // ------------------------------------------------------------------ DELETE /api/food-menu/{id}

    // Удаление позиции меню — только ADMIN
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteFoodItem(@PathVariable Long id) {
        foodMenuService.deleteFoodItem(id);
        return ResponseEntity.noContent().build(); // HTTP 204 No Content
    }
}
