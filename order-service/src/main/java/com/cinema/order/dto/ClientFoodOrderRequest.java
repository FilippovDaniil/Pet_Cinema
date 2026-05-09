package com.cinema.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ClientFoodOrderRequest {

    @NotEmpty(message = "Items list must not be empty")
    private List<@Valid ItemRequest> items;

    @Data
    public static class ItemRequest {
        @NotNull(message = "Food item ID must not be null")
        private Long foodItemId;
        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;
    }
}
