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
    private final ExtraServiceRepository extraServiceRepository; // Для getExtraServicesForSession

    // Возвращает список сеансов с опциональными фильтрами.
    // Логика приоритета фильтров:
    //   1. Если задан movieId → используем оптимизированный запрос findByMovieIdAndActive()
    //   2. Если задан только hallId → используем findByHallIdAndActive()
    //   3. Если ничего не задано → загружаем всё и фильтруем в Java
    //
    // Затем применяем дополнительные фильтры в Java (Stream):
    //   - hallId (дополнительная проверка, если movieId задан вместе с hallId)
    //   - from / to (временной диапазон)
    @Transactional(readOnly = true)
    public List<SessionDto> getSessions(Long movieId, Long hallId, LocalDateTime from, LocalDateTime to) {
        List<Session> base;
        if (movieId != null) {
            // Приоритет: фильтр по фильму — наиболее частый случай (клиент смотрит сеансы фильма)
            base = sessionRepository.findByMovieIdAndActive(movieId, true);
        } else if (hallId != null) {
            // Фильтр по залу — для административных задач (посмотреть загрузку зала)
            base = sessionRepository.findByHallIdAndActive(hallId, true);
        } else {
            // Нет специфичных фильтров → берём все и фильтруем active в Java
            // (findAll() не имеет параметра active, поэтому фильтруем через Stream)
            base = sessionRepository.findAll().stream()
                    .filter(Session::isActive) // Ссылка на метод: s -> s.isActive()
                    .collect(Collectors.toList());
        }
        return base.stream()
                // Если передан hallId вместе с movieId — дофильтровываем по залу в Java.
                // null == не фильтровать.
                .filter(s -> hallId == null || s.getHall().getId().equals(hallId))
                // from — нижняя граница: сеансы, начинающиеся не РАНЬШЕ from.
                // !isBefore(from) = startTime >= from (isBefore возвращает false если равны)
                .filter(s -> from == null || !s.getStartTime().isBefore(from))
                // to — верхняя граница: сеансы, начинающиеся не ПОЗЖЕ to.
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
        // Загружаем Hall из БД — нужен для установки @ManyToOne связи
        Hall hall = hallRepository.findById(request.getHallId())
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + request.getHallId()));
        Session session = Session.builder()
                .movieId(request.getMovieId())   // ID фильма из movie-service (только скалярное значение)
                .hall(hall)                       // FK на Hall в той же БД
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .basePrice(request.getBasePrice())
                .active(true) // Новый сеанс сразу активен
                .build();
        Session saved = sessionRepository.save(session);
        return toDto(saved);
    }

    @Transactional
    public SessionDto updateSession(Long id, SessionCreateRequest request) {
        // Находим существующий сеанс (404 если не найден)
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));
        // Загружаем новый зал (может отличаться от старого)
        Hall hall = hallRepository.findById(request.getHallId())
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + request.getHallId()));
        // Обновляем все поля (полная замена)
        session.setMovieId(request.getMovieId());
        session.setHall(hall);
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setBasePrice(request.getBasePrice());
        // Не меняем active — обновление не должно активировать деактивированный сеанс
        Session updated = sessionRepository.save(session);
        return toDto(updated);
    }

    // МЯГКОЕ УДАЛЕНИЕ (soft delete): session.active = false вместо физического DELETE.
    // Причина: уже купленные билеты ссылаются на sessionId в order-service.
    // Физическое удаление сломало бы данные билетов.
    // Мягкое удаление скрывает сеанс от API но сохраняет данные для истории заказов.
    @Transactional
    public void deleteSession(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));
        session.setActive(false);           // Помечаем как удалённый
        sessionRepository.save(session);   // UPDATE sessions SET active = false WHERE id = ?
        // НЕ вызываем sessionRepository.delete() или deleteById() — это мягкое удаление!
    }

    // Возвращает доп.услуги зала, в котором проводится данный сеанс.
    // Логика: sessionId → Hall → ExtraServices зала.
    //
    // Используется эндпоинтом GET /api/sessions/{id}/extra-services.
    // order-service вызывает этот эндпоинт через lb://hall-service/api/sessions/{id}/extra-services
    // чтобы узнать цены доп.услуг при оформлении заказа.
    @Transactional(readOnly = true)
    public List<ExtraServiceDto> getExtraServicesForSession(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + sessionId));
        Long hallId = session.getHall().getId(); // LAZY загрузка Hall выполнится здесь
        return extraServiceRepository.findByHallId(hallId).stream()
                .map(es -> ExtraServiceDto.builder()
                        .id(es.getId())
                        .hallId(hallId)
                        .name(es.getName())
                        .price(es.getPrice())
                        .build())
                .collect(Collectors.toList());
    }

    // Приватный метод конвертации Session → SessionDto.
    // session.getHall().getId() — ленивая загрузка Hall допустима в активной транзакции.
    private SessionDto toDto(Session session) {
        return SessionDto.builder()
                .id(session.getId())
                .movieId(session.getMovieId())
                .hallId(session.getHall().getId()) // Навигация через @ManyToOne
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .basePrice(session.getBasePrice())
                .active(session.isActive()) // isActive() — стандартный геттер для boolean поля active
                .build();
    }
}
