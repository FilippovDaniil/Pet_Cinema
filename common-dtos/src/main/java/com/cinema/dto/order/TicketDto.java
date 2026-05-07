package com.cinema.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDto {

    private Long id;
    private Long orderId;
    private Long sessionId;
    private Long userId;
    private int seatRow;
    private int seatNumber;
    private String extraServices;
    private String qrCode;
    private String status;
}
