package com.cinema.dto.hall;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtraServiceDto {

    private Long id;
    private Long hallId;
    private String name;
    private BigDecimal price;
}
