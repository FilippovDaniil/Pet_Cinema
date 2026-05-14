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

// @Service — Spring создаёт Singleton-бин. Семантически указывает на бизнес-логику.
@Service
// @RequiredArgsConstructor — Lombok генерирует конструктор с hallRepository (final-поле).
// Spring вводит зависимость через конструктор (constructor injection).
@RequiredArgsConstructor
public class HallService {

    private final HallRepository hallRepository; // Репозиторий для CRUD-операций с Hall

    // @Transactional(readOnly = true) — оптимизация для SELECT-запросов:
    //   - Hibernate не отслеживает изменения сущностей (no dirty checking)
    //   - Подсказка для БД: можно использовать read-replica
    //   - Слегка быстрее обычной транзакции (меньше накладных расходов)
    @Transactional(readOnly = true)
    public List<HallDto> getAllHalls() {
        return hallRepository.findAll().stream()  // SELECT * FROM halls
                .map(this::toDto)                 // Конвертируем каждую Hall → HallDto
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public HallDto getHallById(Long id) {
        // findById() возвращает Optional<Hall>.
        // orElseThrow() — выбрасывает ResourceNotFoundException если Optional.isEmpty().
        // GlobalExceptionHandler перехватит и вернёт HTTP 404.
        Hall hall = hallRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + id));
        return toDto(hall);
    }

    // @Transactional — без параметров = readOnly = false (транзакция с записью).
    // Используется для операций, изменяющих данные (INSERT/UPDATE/DELETE).
    @Transactional
    public HallDto createHall(HallCreateRequest request) {
        // HallType.valueOf(request.getType()) — преобразует строку "VIP" → HallType.VIP.
        // Если строка не совпадает ни с одним значением enum — кидает IllegalArgumentException.
        // GlobalExceptionHandler.handleGeneral() вернёт HTTP 500 в этом случае.
        // Для более точной обработки можно добавить проверку входного значения в контроллере.
        Hall hall = Hall.builder()
                .name(request.getName())
                .type(HallType.valueOf(request.getType())) // Конвертируем строку в enum
                .rowsCount(request.getRowsCount())
                .seatsPerRow(request.getSeatsPerRow())
                .description(request.getDescription())
                .build();
        Hall saved = hallRepository.save(hall); // INSERT INTO halls ... RETURNING id
        return toDto(saved);
    }

    @Transactional
    public HallDto updateHall(Long id, HallCreateRequest request) {
        // Сначала находим существующий зал (кидает 404 если не найден)
        Hall hall = hallRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + id));
        // Обновляем все поля (полная замена, не патч)
        hall.setName(request.getName());
        hall.setType(HallType.valueOf(request.getType()));
        hall.setRowsCount(request.getRowsCount());
        hall.setSeatsPerRow(request.getSeatsPerRow());
        hall.setDescription(request.getDescription());
        // save() на managed entity — Hibernate генерирует UPDATE (не INSERT),
        // т.к. hall.id уже установлен и запись существует в БД.
        Hall updated = hallRepository.save(hall);
        return toDto(updated);
    }

    @Transactional
    public void deleteHall(Long id) {
        // Находим зал (кидает 404 если не найден) — не допускаем удаления несуществующего зала
        Hall hall = hallRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + id));
        // Жёсткое удаление (hard delete): физически удаляет запись из БД.
        // В отличие от Session (soft delete), зал удаляется полностью.
        // Связанные ExtraService и Session удалятся каскадно через FK ON DELETE CASCADE
        // (если настроено в DDL) или кинут ConstraintViolationException (если нет каскада).
        hallRepository.delete(hall);
    }

    // Вспомогательный метод конвертации сущности Hall → DTO HallDto.
    // public (не private) т.к. используется в других классах (например, в тестах через spy).
    // HallDto возвращает name() типа, а не сам enum — чтобы JSON содержал строку "VIP", не объект.
    public HallDto toDto(Hall hall) {
        return HallDto.builder()
                .id(hall.getId())
                .name(hall.getName())
                .type(hall.getType().name()) // HallType.VIP → "VIP" (строковое представление enum)
                .rowsCount(hall.getRowsCount())
                .seatsPerRow(hall.getSeatsPerRow())
                .description(hall.getDescription())
                .build();
    }
}
