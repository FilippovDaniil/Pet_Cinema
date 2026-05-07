package com.cinema.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieUpdateEvent {

    private Long movieId;
    private String action; // CREATED, UPDATED, DELETED
    private String title;
}
