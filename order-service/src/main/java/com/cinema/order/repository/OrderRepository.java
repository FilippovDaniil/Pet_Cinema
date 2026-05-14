package com.cinema.order.repository;

import com.cinema.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Derived query: Spring Data генерирует SQL "SELECT * FROM orders WHERE user_id = ?".
    // Используется в OrderService.getOrdersByUser() — история заказов клиента.
    List<Order> findByUserId(Long userId);

    // Derived query: "SELECT * FROM orders WHERE seller_id = ?".
    // Используется в OrderService.getOrdersBySeller() — заказы, оформленные конкретным кассиром.
    // sellerId может быть null для клиентских заказов, но null не передаётся в этот метод.
    List<Order> findBySellerId(Long sellerId);
}
