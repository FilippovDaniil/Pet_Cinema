package com.cinema.dto.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodOrderRequest {

    @NotNull(message = "Client ID must not be null")
    private Long clientId;

    @NotEmpty(message = "Items list must not be empty")
    private List<FoodOrderItemRequest> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FoodOrderItemRequest {

        @NotNull(message = "Food item ID must not be null")
        private Long foodItemId;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;
    }
}
