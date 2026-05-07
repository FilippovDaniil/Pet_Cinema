package com.cinema.order.config;

import com.cinema.order.entity.FoodCategory;
import com.cinema.order.entity.FoodItem;
import com.cinema.order.repository.FoodItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final FoodItemRepository foodItemRepository;

    @Override
    public void run(String... args) {
        if (foodItemRepository.count() == 0) {
            log.info("Loading initial food menu data...");

            List<FoodItem> items = List.of(
                    FoodItem.builder()
                            .name("Попкорн большой")
                            .price(new BigDecimal("250.00"))
                            .category(FoodCategory.POPCORN)
                            .build(),
                    FoodItem.builder()
                            .name("Кола 0.5л")
                            .price(new BigDecimal("150.00"))
                            .category(FoodCategory.DRINK)
                            .build(),
                    FoodItem.builder()
                            .name("Начос с соусом")
                            .price(new BigDecimal("200.00"))
                            .category(FoodCategory.SNACK)
                            .build(),
                    FoodItem.builder()
                            .name("Вода 0.5л")
                            .price(new BigDecimal("80.00"))
                            .category(FoodCategory.DRINK)
                            .build(),
                    FoodItem.builder()
                            .name("Хот-дог")
                            .price(new BigDecimal("180.00"))
                            .category(FoodCategory.SNACK)
                            .build(),
                    FoodItem.builder()
                            .name("Капкейк")
                            .price(new BigDecimal("120.00"))
                            .category(FoodCategory.OTHER)
                            .build()
            );

            foodItemRepository.saveAll(items);
            log.info("Food menu initialized with {} items", items.size());
        }
    }
}
