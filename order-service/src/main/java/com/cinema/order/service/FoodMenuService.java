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

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodMenuService {

    private final FoodItemRepository foodItemRepository;

    @Transactional(readOnly = true)
    public List<FoodItemDto> getAllFoodItems() {
        return foodItemRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public FoodItemDto addFoodItem(FoodItemDto dto) {
        FoodItem item = FoodItem.builder()
                .name(dto.getName())
                .price(dto.getPrice())
                .category(FoodCategory.valueOf(dto.getCategory()))
                .build();
        FoodItem saved = foodItemRepository.save(item);
        log.info("Added food item: {}", saved.getName());
        return toDto(saved);
    }

    @Transactional
    public FoodItemDto updateFoodItem(Long id, FoodItemDto dto) {
        FoodItem item = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food item not found: " + id));
        item.setName(dto.getName());
        item.setPrice(dto.getPrice());
        item.setCategory(FoodCategory.valueOf(dto.getCategory()));
        FoodItem saved = foodItemRepository.save(item);
        log.info("Updated food item: {}", saved.getName());
        return toDto(saved);
    }

    @Transactional
    public void deleteFoodItem(Long id) {
        foodItemRepository.deleteById(id);
        log.info("Deleted food item: {}", id);
    }

    private FoodItemDto toDto(FoodItem item) {
        return FoodItemDto.builder()
                .id(item.getId())
                .name(item.getName())
                .price(item.getPrice())
                .category(item.getCategory().name())
                .build();
    }
}
