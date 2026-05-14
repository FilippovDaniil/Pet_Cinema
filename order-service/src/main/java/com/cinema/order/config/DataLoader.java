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

// @Slf4j — Lombok: генерирует поле log для логирования
@Slf4j
// @Component — Spring автоматически обнаруживает и создаёт этот бин
@Component
// @RequiredArgsConstructor — Lombok: конструктор для final поля foodItemRepository
@RequiredArgsConstructor
// CommandLineRunner — интерфейс Spring Boot. Метод run() вызывается ПОСЛЕ полного старта Spring Context.
// Используется для инициализации данных при первом запуске приложения.
public class DataLoader implements CommandLineRunner {

    // Репозиторий для работы с таблицей food_items
    private final FoodItemRepository foodItemRepository;

    @Override
    public void run(String... args) {
        // Идемпотентная проверка: если меню уже загружено — пропускаем.
        // count() == 0 — таблица food_items пустая (первый запуск или после очистки БД).
        // Защищает от дублирования при перезапуске приложения без очистки БД.
        if (foodItemRepository.count() == 0) {
            log.info("Loading initial food menu data...");

            // Создаём 6 позиций меню с Builder паттерном (Lombok @Builder в FoodItem)
            List<FoodItem> items = List.of(
                    FoodItem.builder()
                            .name("Попкорн большой")
                            .price(new BigDecimal("250.00"))  // BigDecimal — точное денежное значение
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
                            .category(FoodCategory.OTHER)   // OTHER — не попкорн, не напиток, не снек
                            .build()
            );

            // saveAll() — batch INSERT: один SQL запрос вместо 6 отдельных.
            // Hibernate оптимизирует: INSERT INTO food_items VALUES (...), (...), ...
            foodItemRepository.saveAll(items);
            log.info("Food menu initialized with {} items", items.size());
        }
    }
}
