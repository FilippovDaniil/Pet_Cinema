package com.cinema.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPurchaseEvent {

    private Long orderId;
    private Long userId;
    private String movieTitle;
    private String sessionTime;
    private BigDecimal totalPrice;
}
