package com.cinema.dto.movie;

import jakarta.validation.constraints.NotBlank;
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
public class MovieCreateRequest {

    @NotBlank(message = "Title must not be blank")
    private String title;

    private String description;

    private String posterUrl;

    @Positive(message = "Duration must be a positive number")
    private int durationMinutes;

    @NotBlank(message = "Type must not be blank")
    private String type;

    @NotNull(message = "Genre IDs must not be null")
    private List<Long> genreIds;
}
