package com.cinema.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDto {

    private Long id;
    private Long orderId;
    private String itemType;
    private Long ticketSessionId;
    private int ticketSeatRow;
    private int ticketSeatNumber;
    private String ticketExtraServices;
    private Long foodItemId;
    private int quantity;
    private BigDecimal price;
}
