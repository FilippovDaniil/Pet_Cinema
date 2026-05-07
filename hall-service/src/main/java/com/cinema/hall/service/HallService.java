package com.cinema.hall.service;

import com.cinema.dto.hall.HallCreateRequest;
import com.cinema.dto.hall.HallDto;
import com.cinema.hall.entity.Hall;
import com.cinema.hall.entity.HallType;
import com.cinema.hall.exception.ResourceNotFoundException;
import com.cinema.hall.repository.HallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HallService {

    private final HallRepository hallRepository;

    @Transactional(readOnly = true)
    public List<HallDto> getAllHalls() {
        return hallRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HallDto getHallById(Long id) {
        Hall hall = hallRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + id));
        return toDto(hall);
    }

    @Transactional
    public HallDto createHall(HallCreateRequest request) {
        Hall hall = Hall.builder()
                .name(request.getName())
                .type(HallType.valueOf(request.getType()))
                .rowsCount(request.getRowsCount())
                .seatsPerRow(request.getSeatsPerRow())
                .description(request.getDescription())
                .build();
        Hall saved = hallRepository.save(hall);
        return toDto(saved);
    }

    @Transactional
    public HallDto updateHall(Long id, HallCreateRequest request) {
        Hall hall = hallRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + id));
        hall.setName(request.getName());
        hall.setType(HallType.valueOf(request.getType()));
        hall.setRowsCount(request.getRowsCount());
        hall.setSeatsPerRow(request.getSeatsPerRow());
        hall.setDescription(request.getDescription());
        Hall updated = hallRepository.save(hall);
        return toDto(updated);
    }

    @Transactional
    public void deleteHall(Long id) {
        Hall hall = hallRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + id));
        hallRepository.delete(hall);
    }

    public HallDto toDto(Hall hall) {
        return HallDto.builder()
                .id(hall.getId())
                .name(hall.getName())
                .type(hall.getType().name())
                .rowsCount(hall.getRowsCount())
                .seatsPerRow(hall.getSeatsPerRow())
                .description(hall.getDescription())
                .build();
    }
}
