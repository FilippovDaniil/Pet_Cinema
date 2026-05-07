package com.cinema.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemType itemType;

    // Ticket-specific fields
    private Long ticketSessionId;
    private int ticketSeatRow;
    private int ticketSeatNumber;

    @Column(columnDefinition = "TEXT")
    private String ticketExtraServices;

    // Food-specific fields
    private Long foodItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id", insertable = false, updatable = false)
    private FoodItem foodItem;

    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
}
