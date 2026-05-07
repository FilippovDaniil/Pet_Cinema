package com.cinema.dto.hall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HallDto {

    private Long id;
    private String name;
    private String type;
    private int rowsCount;
    private int seatsPerRow;
    private String description;
}
