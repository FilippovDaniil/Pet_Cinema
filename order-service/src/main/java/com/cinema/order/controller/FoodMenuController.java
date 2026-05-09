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

@RestController
@RequestMapping("/api/food-menu")
@RequiredArgsConstructor
public class FoodMenuController {

    private final FoodMenuService foodMenuService;

    /**
     * PUBLIC: Get all food menu items
     */
    @GetMapping
    public ResponseEntity<List<FoodItemDto>> getAllFoodItems() {
        return ResponseEntity.ok(foodMenuService.getAllFoodItems());
    }

    /**
     * ADMIN: Add a new food item to the menu
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<FoodItemDto> addFoodItem(@Valid @RequestBody FoodItemDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foodMenuService.addFoodItem(dto));
    }

    /**
     * ADMIN: Update a food item
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<FoodItemDto> updateFoodItem(
            @PathVariable Long id,
            @Valid @RequestBody FoodItemDto dto) {
        return ResponseEntity.ok(foodMenuService.updateFoodItem(id, dto));
    }

    /**
     * ADMIN: Delete a food item
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteFoodItem(@PathVariable Long id) {
        foodMenuService.deleteFoodItem(id);
        return ResponseEntity.noContent().build();
    }
}
