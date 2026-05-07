package com.cinema.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerTicketOrderRequest {

    @NotNull(message = "Client ID must not be null")
    private Long clientId;

    @NotNull(message = "Session ID must not be null")
    private Long sessionId;

    @Positive(message = "Seat row must be a positive number")
    private int seatRow;

    @Positive(message = "Seat number must be a positive number")
    private int seatNumber;

    private List<Long> extraServiceIds;
}
