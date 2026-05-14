package com.cinema.hall.service;

import com.cinema.dto.hall.HallCreateRequest;
import com.cinema.dto.hall.HallDto;
import com.cinema.hall.entity.Hall;
import com.cinema.hall.entity.HallType;
import com.cinema.hall.exception.ResourceNotFoundException;
import com.cinema.hall.repository.HallRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class) — подключает Mockito к JUnit 5.
// Автоматически обрабатывает @Mock и @InjectMocks (создаёт моки и внедряет их).
// Без расширения нужно было бы вызывать MockitoAnnotations.openMocks(this) вручную в @BeforeEach.
@ExtendWith(MockitoExtension.class)
class HallServiceTest {

    // @Mock — Mockito создаёт фиктивный (mock) объект HallRepository.
    // Все методы по умолчанию возвращают "пустые" значения (null / 0 / empty collections).
    // Поведение задаётся через when(...).thenReturn(...) в каждом тесте.
    @Mock
    private HallRepository hallRepository;

    // @InjectMocks — Mockito создаёт реальный HallService и внедряет в него hallRepository-мок.
    // Это позволяет тестировать реальную логику HallService без Spring Context (быстрее).
    @InjectMocks
    private HallService hallService;

    // ------------------------------------------------------------------ getAllHalls

    @Test
    void getAllHalls_returnsDtoList() {
        // Создаём тестовые сущности Hall с разными типами
        Hall hall1 = Hall.builder().id(1L).name("Alpha").type(HallType.NORMAL)
                .rowsCount(10).seatsPerRow(20).description("desc1").build();
        Hall hall2 = Hall.builder().id(2L).name("Beta").type(HallType.VIP)
                .rowsCount(5).seatsPerRow(10).description("desc2").build();

        // Настраиваем мок: hallRepository.findAll() → вернуть наш список
        when(hallRepository.findAll()).thenReturn(List.of(hall1, hall2));

        List<HallDto> result = hallService.getAllHalls();

        // Проверяем что сервис вернул 2 элемента с корректными полями
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Alpha");
        // Проверяем что тип сохранился как строка "NORMAL" (hall.getType().name())
        assertThat(result.get(0).getType()).isEqualTo("NORMAL");
        assertThat(result.get(0).getRowsCount()).isEqualTo(10);
        assertThat(result.get(0).getSeatsPerRow()).isEqualTo(20);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getType()).isEqualTo("VIP");
    }

    // ------------------------------------------------------------------ getHallById

    @Test
    void getHallById_found_returnsCorrectDto() {
        Hall hall = Hall.builder().id(42L).name("Cinema 1").type(HallType.THREE_D)
                .rowsCount(8).seatsPerRow(15).description("3D hall").build();

        // findById возвращает Optional — мокируем Optional.of(hall) для найденного случая
        when(hallRepository.findById(42L)).thenReturn(Optional.of(hall));

        HallDto dto = hallService.getHallById(42L);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getName()).isEqualTo("Cinema 1");
        assertThat(dto.getType()).isEqualTo("THREE_D"); // enum → строка
        assertThat(dto.getRowsCount()).isEqualTo(8);
        assertThat(dto.getSeatsPerRow()).isEqualTo(15);
        assertThat(dto.getDescription()).isEqualTo("3D hall");
    }

    @Test
    void getHallById_notFound_throwsResourceNotFoundException() {
        // Optional.empty() — зал с id=99 не найден
        when(hallRepository.findById(99L)).thenReturn(Optional.empty());

        // assertThatThrownBy — проверяем что метод кидает исключение нужного типа с нужным сообщением
        assertThatThrownBy(() -> hallService.getHallById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99"); // Сообщение должно содержать id для диагностики
    }

    // ------------------------------------------------------------------ createHall

    @Test
    void createHall_success_setsHallTypeVip() {
        HallCreateRequest request = HallCreateRequest.builder()
                .name("VIP Lounge")
                .type("VIP")          // Строка "VIP" → конвертируется в HallType.VIP
                .rowsCount(4)
                .seatsPerRow(6)
                .description("luxury")
                .build();

        Hall saved = Hall.builder().id(10L).name("VIP Lounge").type(HallType.VIP)
                .rowsCount(4).seatsPerRow(6).description("luxury").build();

        when(hallRepository.save(any(Hall.class))).thenReturn(saved);

        HallDto result = hallService.createHall(request);

        // ArgumentCaptor — перехватывает аргумент, переданный в hallRepository.save().
        // Позволяет проверить что сервис сконструировал объект Hall корректно ДО сохранения.
        // Альтернатива: verify(hallRepository).save(argThat(h -> h.getType() == HallType.VIP))
        ArgumentCaptor<Hall> captor = ArgumentCaptor.forClass(Hall.class);
        verify(hallRepository).save(captor.capture()); // Захватываем аргумент
        Hall captured = captor.getValue();             // Достаём захваченный объект

        // Проверяем что тип был корректно сконвертирован из строки в enum
        assertThat(captured.getType()).isEqualTo(HallType.VIP);
        assertThat(captured.getName()).isEqualTo("VIP Lounge");
        assertThat(captured.getRowsCount()).isEqualTo(4);
        assertThat(captured.getSeatsPerRow()).isEqualTo(6);
        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getType()).isEqualTo("VIP"); // DTO содержит строку
    }

    @Test
    void createHall_invalidType_propagatesIllegalArgumentException() {
        // "INVALID_TYPE" не существует в HallType — HallType.valueOf() кинет IllegalArgumentException
        HallCreateRequest request = HallCreateRequest.builder()
                .name("Bad Hall")
                .type("INVALID_TYPE") // Некорректное значение
                .rowsCount(5)
                .seatsPerRow(10)
                .build();

        assertThatThrownBy(() -> hallService.createHall(request))
                .isInstanceOf(IllegalArgumentException.class);

        // Если исключение кинуто ДО save() — репозиторий не должен вызываться
        verify(hallRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ updateHall

    @Test
    void updateHall_success_updatesAllFields() {
        Hall existing = Hall.builder().id(1L).name("Old Name").type(HallType.NORMAL)
                .rowsCount(5).seatsPerRow(8).description("old desc").build();

        HallCreateRequest request = HallCreateRequest.builder()
                .name("New Name")
                .type("FIVE_D")       // Меняем тип зала
                .rowsCount(12)
                .seatsPerRow(20)
                .description("new desc")
                .build();

        Hall updated = Hall.builder().id(1L).name("New Name").type(HallType.FIVE_D)
                .rowsCount(12).seatsPerRow(20).description("new desc").build();

        when(hallRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(hallRepository.save(any(Hall.class))).thenReturn(updated);

        HallDto result = hallService.updateHall(1L, request);

        // Проверяем DTO результата
        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getType()).isEqualTo("FIVE_D");
        assertThat(result.getRowsCount()).isEqualTo(12);
        assertThat(result.getSeatsPerRow()).isEqualTo(20);
        assertThat(result.getDescription()).isEqualTo("new desc");

        // Дополнительно: проверяем что в save() передали объект с правильными полями
        ArgumentCaptor<Hall> captor = ArgumentCaptor.forClass(Hall.class);
        verify(hallRepository).save(captor.capture());
        Hall saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("New Name");
        assertThat(saved.getType()).isEqualTo(HallType.FIVE_D);
    }

    @Test
    void updateHall_notFound_throwsResourceNotFoundException() {
        // Зал с id=55 не найден → 404
        when(hallRepository.findById(55L)).thenReturn(Optional.empty());

        HallCreateRequest request = HallCreateRequest.builder()
                .name("Any").type("NORMAL").rowsCount(5).seatsPerRow(10).build();

        assertThatThrownBy(() -> hallService.updateHall(55L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("55");

        // Если зал не найден — save() не вызывается
        verify(hallRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ deleteHall

    @Test
    void deleteHall_success_callsRepositoryDelete() {
        Hall hall = Hall.builder().id(7L).name("To Delete").type(HallType.NORMAL)
                .rowsCount(3).seatsPerRow(5).build();

        when(hallRepository.findById(7L)).thenReturn(Optional.of(hall));

        hallService.deleteHall(7L);

        // Проверяем что hallRepository.delete(hall) был вызван (жёсткое удаление)
        verify(hallRepository).delete(hall);
    }

    @Test
    void deleteHall_notFound_throwsResourceNotFoundException() {
        when(hallRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hallService.deleteHall(100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("100");

        // Если зал не найден — delete() не вызывается
        verify(hallRepository, never()).delete(any());
    }
}
