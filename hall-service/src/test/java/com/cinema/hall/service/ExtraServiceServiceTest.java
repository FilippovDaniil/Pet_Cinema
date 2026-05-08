package com.cinema.hall.service;

import com.cinema.dto.hall.ExtraServiceCreateRequest;
import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.hall.entity.ExtraService;
import com.cinema.hall.entity.Hall;
import com.cinema.hall.entity.HallType;
import com.cinema.hall.exception.ResourceNotFoundException;
import com.cinema.hall.repository.ExtraServiceRepository;
import com.cinema.hall.repository.HallRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExtraServiceServiceTest {

    @Mock
    private ExtraServiceRepository extraServiceRepository;

    @Mock
    private HallRepository hallRepository;

    @InjectMocks
    private ExtraServiceService extraServiceService;

    private Hall buildHall(Long id) {
        return Hall.builder()
                .id(id)
                .name("Hall " + id)
                .type(HallType.VIP)
                .rowsCount(5)
                .seatsPerRow(10)
                .build();
    }

    // ------------------------------------------------------------------ getExtraServicesByHallId

    @Test
    void getExtraServicesByHallId_returnsList() {
        Hall hall = buildHall(1L);
        ExtraService es1 = ExtraService.builder().id(10L).hall(hall).name("Popcorn").price(new BigDecimal("5.00")).build();
        ExtraService es2 = ExtraService.builder().id(11L).hall(hall).name("Soda").price(new BigDecimal("3.00")).build();

        when(hallRepository.existsById(1L)).thenReturn(true);
        when(extraServiceRepository.findByHallId(1L)).thenReturn(List.of(es1, es2));

        List<ExtraServiceDto> result = extraServiceService.getExtraServicesByHallId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getHallId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Popcorn");
        assertThat(result.get(0).getPrice()).isEqualByComparingTo("5.00");
        assertThat(result.get(1).getName()).isEqualTo("Soda");
    }

    @Test
    void getExtraServicesByHallId_hallNotFound_throwsResourceNotFoundException() {
        when(hallRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> extraServiceService.getExtraServicesByHallId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(extraServiceRepository, never()).findByHallId(any());
    }

    // ------------------------------------------------------------------ addExtraService

    @Test
    void addExtraService_success_createsWithCorrectHallReference() {
        Hall hall = buildHall(2L);
        ExtraServiceCreateRequest request = ExtraServiceCreateRequest.builder()
                .name("Nachos")
                .price(new BigDecimal("7.50"))
                .build();

        ExtraService saved = ExtraService.builder().id(20L).hall(hall).name("Nachos")
                .price(new BigDecimal("7.50")).build();

        when(hallRepository.findById(2L)).thenReturn(Optional.of(hall));
        when(extraServiceRepository.save(any(ExtraService.class))).thenReturn(saved);

        ExtraServiceDto result = extraServiceService.addExtraService(2L, request);

        ArgumentCaptor<ExtraService> captor = ArgumentCaptor.forClass(ExtraService.class);
        verify(extraServiceRepository).save(captor.capture());
        ExtraService captured = captor.getValue();

        assertThat(captured.getHall()).isEqualTo(hall);
        assertThat(captured.getHall().getId()).isEqualTo(2L);
        assertThat(captured.getName()).isEqualTo("Nachos");
        assertThat(captured.getPrice()).isEqualByComparingTo("7.50");

        assertThat(result.getId()).isEqualTo(20L);
        assertThat(result.getHallId()).isEqualTo(2L);
        assertThat(result.getName()).isEqualTo("Nachos");
        assertThat(result.getPrice()).isEqualByComparingTo("7.50");
    }

    @Test
    void addExtraService_hallNotFound_throwsResourceNotFoundException() {
        when(hallRepository.findById(77L)).thenReturn(Optional.empty());

        ExtraServiceCreateRequest request = ExtraServiceCreateRequest.builder()
                .name("Water")
                .price(new BigDecimal("2.00"))
                .build();

        assertThatThrownBy(() -> extraServiceService.addExtraService(77L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("77");

        verify(extraServiceRepository, never()).save(any());
    }
}
