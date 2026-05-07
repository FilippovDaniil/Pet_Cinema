package com.cinema.hall.controller;

import com.cinema.dto.hall.ExtraServiceCreateRequest;
import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.HallCreateRequest;
import com.cinema.dto.hall.HallDto;
import com.cinema.hall.service.ExtraServiceService;
import com.cinema.hall.service.HallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/halls")
@RequiredArgsConstructor
public class HallController {

    private final HallService hallService;
    private final ExtraServiceService extraServiceService;

    @GetMapping
    public ResponseEntity<List<HallDto>> getAllHalls() {
        return ResponseEntity.ok(hallService.getAllHalls());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HallDto> getHallById(@PathVariable Long id) {
        return ResponseEntity.ok(hallService.getHallById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<HallDto> createHall(@Valid @RequestBody HallCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hallService.createHall(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<HallDto> updateHall(
            @PathVariable Long id,
            @Valid @RequestBody HallCreateRequest request
    ) {
        return ResponseEntity.ok(hallService.updateHall(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteHall(@PathVariable Long id) {
        hallService.deleteHall(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/extra-services")
    public ResponseEntity<List<ExtraServiceDto>> getExtraServices(@PathVariable Long id) {
        return ResponseEntity.ok(extraServiceService.getExtraServicesByHallId(id));
    }

    @PostMapping("/{id}/extra-services")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ExtraServiceDto> addExtraService(
            @PathVariable Long id,
            @Valid @RequestBody ExtraServiceCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(extraServiceService.addExtraService(id, request));
    }
}
