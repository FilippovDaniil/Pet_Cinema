package com.cinema.movie.service;

import com.cinema.dto.movie.GenreDto;
import com.cinema.movie.entity.Genre;
import com.cinema.movie.exception.AlreadyExistsException;
import com.cinema.movie.repository.GenreRepository;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// @ExtendWith(MockitoExtension.class) — подключает Mockito к JUnit 5.
// Позволяет использовать @Mock, @InjectMocks без явного вызова MockitoAnnotations.openMocks(this).
// Нет Spring контекста — тест быстрый (миллисекунды, не секунды).
@ExtendWith(MockitoExtension.class)
@DisplayName("GenreService Unit Tests") // Название группы тестов в отчёте
class GenreServiceTest {

    @Mock // Mockito создаёт mock-объект GenreRepository: все методы возвращают null/пустые значения по умолчанию
    private GenreRepository genreRepository;

    @InjectMocks // Mockito создаёт реальный GenreService и инжектирует mock genreRepository в конструктор
    private GenreService genreService;

    // ────────────────────────────────────────────────────────────────────────────
    // getAllGenres tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllGenres: returns all genres mapped to GenreDtos")
    void getAllGenres_returnsMappedDtos() {
        // Arrange: подготавливаем тестовые данные
        Genre action = Genre.builder().id(1L).name("Action").build();
        Genre drama = Genre.builder().id(2L).name("Drama").build();
        Genre comedy = Genre.builder().id(3L).name("Comedy").build();

        // when(...).thenReturn(...): определяем поведение мока.
        // Когда genreRepository.findAll() будет вызван — вернуть этот список.
        when(genreRepository.findAll()).thenReturn(List.of(action, drama, comedy));

        // Act: вызываем тестируемый метод
        List<GenreDto> result = genreService.getAllGenres();

        // Assert: проверяем результат
        assertThat(result).hasSize(3); // Ровно 3 жанра

        // Проверяем маппинг: id и name правильно перенесены из Genre в GenreDto
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Action");

        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getName()).isEqualTo("Drama");

        assertThat(result.get(2).getId()).isEqualTo(3L);
        assertThat(result.get(2).getName()).isEqualTo("Comedy");

        // verify(): проверяем, что метод был вызван ровно 1 раз (по умолчанию times(1))
        verify(genreRepository).findAll();
    }

    @Test
    @DisplayName("getAllGenres: empty repository → returns empty list")
    void getAllGenres_emptyRepository_returnsEmptyList() {
        when(genreRepository.findAll()).thenReturn(List.of()); // Пустой репозиторий

        List<GenreDto> result = genreService.getAllGenres();

        assertThat(result).isEmpty(); // Должен вернуть пустой список, а не null
        verify(genreRepository).findAll(); // Репозиторий всё равно вызывался
    }

    // ────────────────────────────────────────────────────────────────────────────
    // createGenre tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGenre: new name → saves genre and returns GenreDto with assigned id")
    void createGenre_newName_saved() {
        GenreDto request = GenreDto.builder().name("Horror").build(); // Запрос на создание

        Genre savedGenre = Genre.builder().id(4L).name("Horror").build(); // Что "вернёт БД" после save()

        when(genreRepository.findByName("Horror")).thenReturn(Optional.empty()); // Жанра нет → не дубликат
        when(genreRepository.save(any(Genre.class))).thenReturn(savedGenre);     // save() возвращает объект с id

        GenreDto result = genreService.createGenre(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(4L);    // id присвоен "БД"
        assertThat(result.getName()).isEqualTo("Horror");

        // ArgumentCaptor — перехватывает аргумент, переданный в save().
        // Позволяет проверить ЧТО именно было передано в метод (не только что он был вызван).
        ArgumentCaptor<Genre> genreCaptor = ArgumentCaptor.forClass(Genre.class);
        verify(genreRepository).save(genreCaptor.capture()); // Перехватываем аргумент
        assertThat(genreCaptor.getValue().getName()).isEqualTo("Horror"); // Проверяем содержимое
    }

    @Test
    @DisplayName("createGenre: duplicate name → throws AlreadyExistsException and does not save")
    void createGenre_duplicateName_throwsAlreadyExists() {
        GenreDto request = GenreDto.builder().name("Action").build();

        Genre existingGenre = Genre.builder().id(1L).name("Action").build();
        // Жанр "Action" уже существует → findByName возвращает его
        when(genreRepository.findByName("Action")).thenReturn(Optional.of(existingGenre));

        // assertThatThrownBy: проверяем что лямбда бросает нужное исключение
        assertThatThrownBy(() -> genreService.createGenre(request))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessageContaining("Action"); // Сообщение содержит имя дублирующегося жанра

        // never(): подтверждаем что save() НЕ был вызван — дубликат не сохранён
        verify(genreRepository, never()).save(any());
    }

    @Test
    @DisplayName("createGenre: saves genre with correct name from request")
    void createGenre_savesCorrectName() {
        GenreDto request = GenreDto.builder().name("Thriller").build();
        Genre savedGenre = Genre.builder().id(5L).name("Thriller").build();

        when(genreRepository.findByName("Thriller")).thenReturn(Optional.empty());
        when(genreRepository.save(any(Genre.class))).thenReturn(savedGenre);

        GenreDto result = genreService.createGenre(request);

        // Дополнительный тест: убеждаемся что name из request правильно попал в ответ
        assertThat(result.getName()).isEqualTo("Thriller");
        assertThat(result.getId()).isEqualTo(5L);
    }
}
