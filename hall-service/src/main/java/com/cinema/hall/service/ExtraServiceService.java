package com.cinema.hall.service;

import com.cinema.dto.hall.ExtraServiceCreateRequest;
import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.hall.entity.ExtraService;
import com.cinema.hall.entity.Hall;
import com.cinema.hall.exception.ResourceNotFoundException;
import com.cinema.hall.repository.ExtraServiceRepository;
import com.cinema.hall.repository.HallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExtraServiceService {

    private final ExtraServiceRepository extraServiceRepository;
    private final HallRepository hallRepository;

    @Transactional(readOnly = true)
    public List<ExtraServiceDto> getExtraServicesByHallId(Long hallId) {
        if (!hallRepository.existsById(hallId)) {
            throw new ResourceNotFoundException("Hall not found with id: " + hallId);
        }
        return extraServiceRepository.findByHallId(hallId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ExtraServiceDto addExtraService(Long hallId, ExtraServiceCreateRequest request) {
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + hallId));
        ExtraService extraService = ExtraService.builder()
                .hall(hall)
                .name(request.getName())
                .price(request.getPrice())
                .build();
        ExtraService saved = extraServiceRepository.save(extraService);
        return toDto(saved);
    }

    public ExtraServiceDto toDto(ExtraService extraService) {
        return ExtraServiceDto.builder()
                .id(extraService.getId())
                .hallId(extraService.getHall().getId())
                .name(extraService.getName())
                .price(extraService.getPrice())
                .build();
    }
}
