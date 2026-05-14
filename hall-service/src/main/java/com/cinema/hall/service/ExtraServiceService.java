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
    private final HallRepository hallRepository; // Нужен для проверки существования зала

    // Возвращает список доп.услуг зала по hallId.
    // Сначала проверяем что зал существует — возвращаем 404 если нет.
    // (Без проверки вернули бы пустой список даже для несуществующего hallId — не информативно.)
    @Transactional(readOnly = true)
    public List<ExtraServiceDto> getExtraServicesByHallId(Long hallId) {
        // existsById() генерирует SELECT COUNT(*) > 0 FROM halls WHERE id = ?
        // Быстрее чем findById() для простой проверки существования.
        if (!hallRepository.existsById(hallId)) {
            throw new ResourceNotFoundException("Hall not found with id: " + hallId);
        }
        return extraServiceRepository.findByHallId(hallId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    // Добавляет новую доп.услугу к залу.
    // findById() нужен чтобы получить объект Hall для установки FK-связи.
    // (existsById() не подходит — нам нужен сам объект Hall для ExtraService.hall)
    @Transactional
    public ExtraServiceDto addExtraService(Long hallId, ExtraServiceCreateRequest request) {
        // Загружаем Hall из БД для установки связи @ManyToOne
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found with id: " + hallId));
        ExtraService extraService = ExtraService.builder()
                .hall(hall)                 // Устанавливаем FK-связь с залом
                .name(request.getName())
                .price(request.getPrice())
                .build();
        ExtraService saved = extraServiceRepository.save(extraService);
        return toDto(saved);
    }

    // public метод конвертации ExtraService → ExtraServiceDto.
    // public (не private) т.к. используется в SessionService.getExtraServicesForSession()
    // (другой сервис в том же пакете hall-service).
    //
    // extraService.getHall().getId() — ленивая загрузка Hall будет выполнена здесь
    // (если Hall ещё не загружен). Это безопасно пока мы находимся в активной транзакции
    // (@Transactional на вызывающем методе).
    public ExtraServiceDto toDto(ExtraService extraService) {
        return ExtraServiceDto.builder()
                .id(extraService.getId())
                .hallId(extraService.getHall().getId()) // Получаем id зала через навигацию
                .name(extraService.getName())
                .price(extraService.getPrice())
                .build();
    }
}
