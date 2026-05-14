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

// Юнит-тесты FoodMenuService. Нет Spring Context — только Mockito.
// @ExtendWith(MockitoExtension.class) — подключает JUnit 5 расширение Mockito:
//   инициализирует @Mock и @InjectMocks поля перед каждым тестом.
@ExtendWith(MockitoExtension.class)
class FoodMenuServiceTest {

    // @Mock — Mockito создаёт proxy-заглушку вместо реального JPA репозитория.
    // Никакой БД нет — вызовы к репозиторию управляются через when().thenReturn().
    @Mock
    private FoodItemRepository foodItemRepository;

    // @InjectMocks — Mockito создаёт FoodMenuService и инжектирует все @Mock поля.
    // Эквивалент: new FoodMenuService(foodItemRepository)
    @InjectMocks
    private FoodMenuService foodMenuService;

    // @Captor — ArgumentCaptor для захвата аргументов переданных в мок.
    // Позволяет проверить что именно было передано в foodItemRepository.save().
    @Captor
    private ArgumentCaptor<FoodItem> foodItemCaptor;

    // ------------------------------------------------------------------ getAllFoodItems

    @Test
    @DisplayName("getAllFoodItems: returns list of FoodItemDtos mapped from repository")
    void getAllFoodItems_returnsDtoList() {
        // Arrange: мокируем findAll() — возвращает 3 FoodItem
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

        // Assert: все 3 позиции корректно смаппированы в DTO
        assertThat(result).hasSize(3);

        // Проверяем каждую позицию: id, name, price, category (Enum → String)
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Popcorn");
        assertThat(result.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.get(0).getCategory()).isEqualTo("POPCORN"); // Enum.name() → String

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
        // Arrange: пустое меню
        when(foodItemRepository.findAll()).thenReturn(List.of());

        // Act
        List<FoodItemDto> result = foodMenuService.getAllFoodItems();

        // Assert: не null, а пустой список
        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------ addFoodItem

    @Test
    @DisplayName("addFoodItem: saves FoodItem and returns mapped FoodItemDto")
    void addFoodItem_success() {
        // Arrange: входной DTO с category как String
        FoodItemDto inputDto = FoodItemDto.builder()
                .name("Hot Dog")
                .price(new BigDecimal("200.00"))
                .category("SNACK")  // строка, сервис делает FoodCategory.valueOf("SNACK")
                .build();

        // Мок сохранения — возвращает сущность с назначенным id
        FoodItem savedItem = FoodItem.builder()
                .id(10L)
                .name("Hot Dog")
                .price(new BigDecimal("200.00"))
                .category(FoodCategory.SNACK)
                .build();

        when(foodItemRepository.save(any(FoodItem.class))).thenReturn(savedItem);

        // Act
        FoodItemDto result = foodMenuService.addFoodItem(inputDto);

        // Assert: проверяем что в save() передана корректная сущность
        verify(foodItemRepository).save(foodItemCaptor.capture());
        FoodItem capturedItem = foodItemCaptor.getValue();
        assertThat(capturedItem.getName()).isEqualTo("Hot Dog");
        assertThat(capturedItem.getPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(capturedItem.getCategory()).isEqualTo(FoodCategory.SNACK); // String → Enum конвертация

        // Проверяем возвращённый DTO
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Hot Dog");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getCategory()).isEqualTo("SNACK"); // Enum → String обратная конвертация
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

        // Assert: категория DRINK корректно конвертируется в обе стороны
        assertThat(result.getCategory()).isEqualTo("DRINK");
        assertThat(result.getId()).isEqualTo(11L);

        verify(foodItemRepository).save(foodItemCaptor.capture());
        assertThat(foodItemCaptor.getValue().getCategory()).isEqualTo(FoodCategory.DRINK);
    }
}
