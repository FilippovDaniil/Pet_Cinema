package com.cinema.hall.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "halls")
public class Hall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HallType type;

    @Column(name = "rows_count", nullable = false)
    private int rowsCount;

    @Column(name = "seats_per_row", nullable = false)
    private int seatsPerRow;

    @Column(columnDefinition = "TEXT")
    private String description;
}
