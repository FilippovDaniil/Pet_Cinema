package com.cinema.order.service;

import com.cinema.dto.order.FoodItemDto;
import com.cinema.order.entity.FoodCategory;
import com.cinema.order.entity.FoodItem;
import com.cinema.order.repository.FoodItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodMenuServiceTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @InjectMocks
    private FoodMenuService foodMenuService;

    @Captor
    private ArgumentCaptor<FoodItem> foodItemCaptor;

    @Test
    @DisplayName("getAllFoodItems: returns list of FoodItemDtos mapped from repository")
    void getAllFoodItems_returnsDtoList() {
        // Arrange
        FoodItem item1 = FoodItem.builder()
                .id(1L)
                .name("Popcorn")
                .price(new BigDecimal("150.00"))
                .category(FoodCategory.POPCORN)
                .build();
        FoodItem item2 = FoodItem.builder()
                .id(2L)
                .name("Cola")
                .price(new BigDecimal("80.00"))
                .category(FoodCategory.DRINK)
                .build();
        FoodItem item3 = FoodItem.builder()
                .id(3L)
                .name("Nachos")
                .price(new BigDecimal("120.00"))
                .category(FoodCategory.SNACK)
                .build();

        when(foodItemRepository.findAll()).thenReturn(List.of(item1, item2, item3));

        // Act
        List<FoodItemDto> result = foodMenuService.getAllFoodItems();

        // Assert
        assertThat(result).hasSize(3);

        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Popcorn");
        assertThat(result.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.get(0).getCategory()).isEqualTo("POPCORN");

        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getName()).isEqualTo("Cola");
        assertThat(result.get(1).getPrice()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(result.get(1).getCategory()).isEqualTo("DRINK");

        assertThat(result.get(2).getId()).isEqualTo(3L);
        assertThat(result.get(2).getName()).isEqualTo("Nachos");
        assertThat(result.get(2).getPrice()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(result.get(2).getCategory()).isEqualTo("SNACK");
    }

    @Test
    @DisplayName("getAllFoodItems: empty repository returns empty list")
    void getAllFoodItems_emptyRepository_returnsEmptyList() {
        // Arrange
        when(foodItemRepository.findAll()).thenReturn(List.of());

        // Act
        List<FoodItemDto> result = foodMenuService.getAllFoodItems();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("addFoodItem: saves FoodItem and returns mapped FoodItemDto")
    void addFoodItem_success() {
        // Arrange
        FoodItemDto inputDto = FoodItemDto.builder()
                .name("Hot Dog")
                .price(new BigDecimal("200.00"))
                .category("SNACK")
                .build();

        FoodItem savedItem = FoodItem.builder()
                .id(10L)
                .name("Hot Dog")
                .price(new BigDecimal("200.00"))
                .category(FoodCategory.SNACK)
                .build();

        when(foodItemRepository.save(any(FoodItem.class))).thenReturn(savedItem);

        // Act
        FoodItemDto result = foodMenuService.addFoodItem(inputDto);

        // Assert
        verify(foodItemRepository).save(foodItemCaptor.capture());
        FoodItem capturedItem = foodItemCaptor.getValue();
        assertThat(capturedItem.getName()).isEqualTo("Hot Dog");
        assertThat(capturedItem.getPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(capturedItem.getCategory()).isEqualTo(FoodCategory.SNACK);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Hot Dog");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getCategory()).isEqualTo("SNACK");
    }

    @Test
    @DisplayName("addFoodItem: DRINK category is correctly persisted")
    void addFoodItem_drinkCategory_success() {
        // Arrange
        FoodItemDto inputDto = FoodItemDto.builder()
                .name("Juice")
                .price(new BigDecimal("90.00"))
                .category("DRINK")
                .build();

        FoodItem savedItem = FoodItem.builder()
                .id(11L)
                .name("Juice")
                .price(new BigDecimal("90.00"))
                .category(FoodCategory.DRINK)
                .build();

        when(foodItemRepository.save(any(FoodItem.class))).thenReturn(savedItem);

        // Act
        FoodItemDto result = foodMenuService.addFoodItem(inputDto);

        // Assert
        assertThat(result.getCategory()).isEqualTo("DRINK");
        assertThat(result.getId()).isEqualTo(11L);

        verify(foodItemRepository).save(foodItemCaptor.capture());
        assertThat(foodItemCaptor.getValue().getCategory()).isEqualTo(FoodCategory.DRINK);
    }
}
