package com.cinema.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookRequest {

    @NotNull(message = "Order ID must not be null")
    private Long orderId;

    @NotBlank(message = "Status must not be blank")
    private String status;

    @NotBlank(message = "Transaction ID must not be blank")
    private String transactionId;
}
