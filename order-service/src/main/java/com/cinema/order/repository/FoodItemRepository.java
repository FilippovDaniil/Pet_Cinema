package com.cinema.order.repository;

import com.cinema.order.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// @Repository — помечает интерфейс как Spring Bean репозитория.
// JpaRepository<FoodItem, Long> — наследует стандартные CRUD методы:
//   save(), findById(), findAll(), deleteById(), count() и др.
// Кастомные методы не нужны: DataLoader использует findAll() для проверки count(),
// OrderService использует findById() для поиска по id из запроса клиента.
@Repository
public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {
}
