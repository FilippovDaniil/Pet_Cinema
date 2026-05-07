package com.cinema.dto.hall;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HallCreateRequest {

    @NotBlank(message = "Hall name must not be blank")
    private String name;

    @NotBlank(message = "Hall type must not be blank")
    private String type;

    @Positive(message = "Rows count must be a positive number")
    private int rowsCount;

    @Positive(message = "Seats per row must be a positive number")
    private int seatsPerRow;

    private String description;
}
