package com.cinema.order.service;

import com.cinema.dto.order.FoodItemDto;
import com.cinema.order.entity.FoodCategory;
import com.cinema.order.entity.FoodItem;
import com.cinema.order.exception.ResourceNotFoundException;
import com.cinema.order.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

// @Slf4j — Lombok: генерирует поле log
@Slf4j
// @Service — Spring бин бизнес-логики для управления меню
@Service
// @RequiredArgsConstructor — Lombok: конструктор для final поля foodItemRepository
@RequiredArgsConstructor
public class FoodMenuService {

    // Репозиторий для работы с таблицей food_items
    private final FoodItemRepository foodItemRepository;

    // readOnly=true: Hibernate не отслеживает изменения — быстрее для операций чтения
    @Transactional(readOnly = true)
    public List<FoodItemDto> getAllFoodItems() {
        // findAll() — возвращает все позиции меню; .stream().map(toDto) → List<FoodItemDto>
        return foodItemRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FoodItemDto addFoodItem(FoodItemDto dto) {
        // FoodCategory.valueOf() — конвертирует строку "DRINK" в enum FoodCategory.DRINK.
        // Бросает IllegalArgumentException если строка не совпадает ни с одним значением enum.
        FoodItem item = FoodItem.builder()
                .name(dto.getName())
                .price(dto.getPrice())
                .category(FoodCategory.valueOf(dto.getCategory()))
                .build();
        FoodItem saved = foodItemRepository.save(item); // INSERT в БД, возвращает сущность с id
        log.info("Added food item: {}", saved.getName());
        return toDto(saved);
    }

    @Transactional
    public FoodItemDto updateFoodItem(Long id, FoodItemDto dto) {
        // findById() + orElseThrow() — стандартный паттерн получения сущности или 404
        FoodItem item = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food item not found: " + id));

        // Обновляем все поля (partial update не поддерживается — все поля обязательны в dto)
        item.setName(dto.getName());
        item.setPrice(dto.getPrice());
        item.setCategory(FoodCategory.valueOf(dto.getCategory()));

        // save() существующей сущности (с id) = UPDATE, не INSERT
        FoodItem saved = foodItemRepository.save(item);
        log.info("Updated food item: {}", saved.getName());
        return toDto(saved);
    }

    @Transactional
    public void deleteFoodItem(Long id) {
        // deleteById() — физическое удаление из БД (не soft delete).
        // Если id не существует — выбрасывает EmptyResultDataAccessException (Spring Data).
        foodItemRepository.deleteById(id);
        log.info("Deleted food item: {}", id);
    }

    // Конвертирует JPA-сущность FoodItem в DTO для отправки клиенту.
    // category().name() — Enum → String ("DRINK", "POPCORN", etc.)
    private FoodItemDto toDto(FoodItem item) {
        return FoodItemDto.builder()
                .id(item.getId())
                .name(item.getName())
                .price(item.getPrice())
                .category(item.getCategory().name()) // Enum → String
                .build();
    }
}
