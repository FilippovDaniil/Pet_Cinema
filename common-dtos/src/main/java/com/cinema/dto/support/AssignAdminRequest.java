package com.cinema.dto.support;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignAdminRequest {

    @NotNull(message = "Admin ID must not be null")
    private Long adminId;
}
