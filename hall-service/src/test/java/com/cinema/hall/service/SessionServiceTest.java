package com.cinema.hall.service;

import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.SessionCreateRequest;
import com.cinema.dto.hall.SessionDto;
import com.cinema.hall.entity.ExtraService;
import com.cinema.hall.entity.Hall;
import com.cinema.hall.entity.HallType;
import com.cinema.hall.entity.Session;
import com.cinema.hall.exception.ResourceNotFoundException;
import com.cinema.hall.repository.ExtraServiceRepository;
import com.cinema.hall.repository.HallRepository;
import com.cinema.hall.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private ExtraServiceRepository extraServiceRepository;

    @InjectMocks
    private SessionService sessionService;

    private Hall buildHall(Long id) {
        return Hall.builder().id(id).name("Hall " + id).type(HallType.NORMAL)
                .rowsCount(10).seatsPerRow(20).build();
    }

    private Session buildSession(Long id, Hall hall) {
        return Session.builder()
                .id(id)
                .movieId(5L)
                .hall(hall)
                .startTime(LocalDateTime.of(2026, 6, 1, 14, 0))
                .endTime(LocalDateTime.of(2026, 6, 1, 16, 0))
                .basePrice(new BigDecimal("12.50"))
                .active(true)
                .build();
    }

    // ------------------------------------------------------------------ getSessions

    @Test
    void getSessions_noFilters_returnsAllSessions() {
        Hall hall = buildHall(1L);
        Session s1 = buildSession(1L, hall);
        Session s2 = buildSession(2L, hall);

        when(sessionRepository.findByFilters(null, null, null, null)).thenReturn(List.of(s1, s2));

        List<SessionDto> result = sessionService.getSessions(null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getHallId()).isEqualTo(1L);
        assertThat(result.get(0).isActive()).isTrue();
    }

    // ------------------------------------------------------------------ getSessionById

    @Test
    void getSessionById_found_returnsDtoWithHallId() {
        Hall hall = buildHall(3L);
        Session session = buildSession(10L, hall);

        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        SessionDto dto = sessionService.getSessionById(10L);

        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getMovieId()).isEqualTo(5L);
        assertThat(dto.getHallId()).isEqualTo(3L);
        assertThat(dto.getBasePrice()).isEqualByComparingTo("12.50");
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    void getSessionById_notFound_throwsResourceNotFoundException() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSessionById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ------------------------------------------------------------------ createSession

    @Test
    void createSession_success_setsActiveTrue() {
        Hall hall = buildHall(2L);
        SessionCreateRequest request = SessionCreateRequest.builder()
                .movieId(7L)
                .hallId(2L)
                .startTime(LocalDateTime.of(2026, 7, 10, 10, 0))
                .endTime(LocalDateTime.of(2026, 7, 10, 12, 0))
                .basePrice(new BigDecimal("15.00"))
                .build();

        Session saved = Session.builder().id(50L).movieId(7L).hall(hall)
                .startTime(request.getStartTime()).endTime(request.getEndTime())
                .basePrice(new BigDecimal("15.00")).active(true).build();

        when(hallRepository.findById(2L)).thenReturn(Optional.of(hall));
        when(sessionRepository.save(any(Session.class))).thenReturn(saved);

        SessionDto result = sessionService.createSession(request);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session captured = captor.getValue();

        assertThat(captured.isActive()).isTrue();
        assertThat(captured.getHall()).isEqualTo(hall);
        assertThat(captured.getMovieId()).isEqualTo(7L);

        assertThat(result.getId()).isEqualTo(50L);
        assertThat(result.isActive()).isTrue();
        assertThat(result.getHallId()).isEqualTo(2L);
    }

    @Test
    void createSession_hallNotFound_throwsResourceNotFoundException() {
        SessionCreateRequest request = SessionCreateRequest.builder()
                .movieId(1L).hallId(88L)
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now().plusHours(2))
                .basePrice(new BigDecimal("10.00"))
                .build();

        when(hallRepository.findById(88L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.createSession(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("88");

        verify(sessionRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ updateSession

    @Test
    void updateSession_success_updatesAllFields() {
        Hall oldHall = buildHall(1L);
        Hall newHall = buildHall(2L);
        Session existing = buildSession(5L, oldHall);

        SessionCreateRequest request = SessionCreateRequest.builder()
                .movieId(9L)
                .hallId(2L)
                .startTime(LocalDateTime.of(2026, 8, 1, 18, 0))
                .endTime(LocalDateTime.of(2026, 8, 1, 20, 0))
                .basePrice(new BigDecimal("20.00"))
                .build();

        Session updated = Session.builder().id(5L).movieId(9L).hall(newHall)
                .startTime(request.getStartTime()).endTime(request.getEndTime())
                .basePrice(new BigDecimal("20.00")).active(true).build();

        when(sessionRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(hallRepository.findById(2L)).thenReturn(Optional.of(newHall));
        when(sessionRepository.save(any(Session.class))).thenReturn(updated);

        SessionDto result = sessionService.updateSession(5L, request);

        assertThat(result.getMovieId()).isEqualTo(9L);
        assertThat(result.getHallId()).isEqualTo(2L);
        assertThat(result.getBasePrice()).isEqualByComparingTo("20.00");
    }

    // ------------------------------------------------------------------ deleteSession (soft delete)

    @Test
    void deleteSession_softDelete_setsActiveFalseDoesNotCallDelete() {
        Hall hall = buildHall(1L);
        Session session = buildSession(3L, hall);
        assertThat(session.isActive()).isTrue();

        when(sessionRepository.findById(3L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenReturn(session);

        sessionService.deleteSession(3L);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();

        // Must NOT call hard delete
        verify(sessionRepository, never()).delete(any(Session.class));
        verify(sessionRepository, never()).deleteById(any());
    }

    // ------------------------------------------------------------------ getExtraServicesForSession

    @Test
    void getExtraServicesForSession_success_getsHallExtraServices() {
        Hall hall = buildHall(4L);
        Session session = buildSession(8L, hall);
        ExtraService es = ExtraService.builder().id(30L).hall(hall).name("Blanket")
                .price(new BigDecimal("3.00")).build();

        when(sessionRepository.findById(8L)).thenReturn(Optional.of(session));
        when(extraServiceRepository.findByHallId(4L)).thenReturn(List.of(es));

        List<ExtraServiceDto> result = sessionService.getExtraServicesForSession(8L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(30L);
        assertThat(result.get(0).getHallId()).isEqualTo(4L);
        assertThat(result.get(0).getName()).isEqualTo("Blanket");
    }
}
