package com.cinema.hall.service;

import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.SessionCreateRequest;
import com.cinema.dto.hall.SessionDto;
import com.cinema.hall.entity.Hall;
import com.cinema.hall.entity.Session;
import com.cinema.hall.exception.ResourceNotFoundException;
import com.cinema.hall.repository.ExtraServiceRepository;
import com.cinema.hall.repository.HallRepository;
import com.cinema.hall.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final HallRepository hallRepository;
    private final ExtraServiceRepository extraServiceRepository;

    @Transactional(readOnly = true)
    public List<SessionDto> getSessions(Long movieId, Long hallId, LocalDateTime from, LocalDateTime to) {
        List<Session> base;
        if (movieId != null) {
            base = sessionRepository.findByMovieIdAndActive(movieId, true);
        } else if (hallId != null) {
            base = sessionRepository.findByHallIdAndActive(hallId, true);
        } else {
            base = sessionRepository.findAll().stream()
                    .filter(Session::isActive)
                    .collect(Collectors.toList());
        }
        return base.stream()
                .filter(s -> hallId == null || s.getHall().getId().equals(hallId))
                .filter(s -> from == null || !s.getStartTime().isBefore(from))
                .filter(s -> to == null || !s.getStartTime().isAfter(to))
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SessionDto getSessionById(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));
        return toDto(session);
    }

    @Transactional
    public SessionDto createSession(SessionCreateRequest request) {
        Hall hall = hallRepository.findById(request.getHallId())
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + request.getHallId()));
        Session session = Session.builder()
                .movieId(request.getMovieId())
                .hall(hall)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .basePrice(request.getBasePrice())
                .active(true)
                .build();
        Session saved = sessionRepository.save(session);
        return toDto(saved);
    }

    @Transactional
    public SessionDto updateSession(Long id, SessionCreateRequest request) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));
        Hall hall = hallRepository.findById(request.getHallId())
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + request.getHallId()));
        session.setMovieId(request.getMovieId());
        session.setHall(hall);
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setBasePrice(request.getBasePrice());
        Session updated = sessionRepository.save(session);
        return toDto(updated);
    }

    @Transactional
    public void deleteSession(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));
        session.setActive(false);
        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<ExtraServiceDto> getExtraServicesForSession(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));
        Long hallId = session.getHall().getId();
        return extraServiceRepository.findByHallId(hallId).stream()
                .map(es -> ExtraServiceDto.builder()
                        .id(es.getId())
                        .hallId(hallId)
                        .name(es.getName())
                        .price(es.getPrice())
                        .build())
                .collect(Collectors.toList());
    }

    private SessionDto toDto(Session session) {
        return SessionDto.builder()
                .id(session.getId())
                .movieId(session.getMovieId())
                .hallId(session.getHall().getId())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .basePrice(session.getBasePrice())
                .active(session.isActive())
                .build();
    }
}
