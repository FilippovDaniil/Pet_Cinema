package com.cinema.order.repository;

import com.cinema.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// @Repository — регистрирует как Spring Bean.
// JpaRepository<OrderItem, Long> — стандартные CRUD методы.
// Кастомные методы не нужны: OrderItem всегда загружается через Order.items (cascade EAGER).
// Прямые запросы к OrderItemRepository в сервисах не используются.
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
