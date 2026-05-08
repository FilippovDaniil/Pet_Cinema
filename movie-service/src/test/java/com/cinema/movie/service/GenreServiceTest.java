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

@ExtendWith(MockitoExtension.class)
@DisplayName("GenreService Unit Tests")
class GenreServiceTest {

    @Mock
    private GenreRepository genreRepository;

    @InjectMocks
    private GenreService genreService;

    // ────────────────────────────────────────────────────────────────────────────
    // getAllGenres tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllGenres: returns all genres mapped to GenreDtos")
    void getAllGenres_returnsMappedDtos() {
        Genre action = Genre.builder().id(1L).name("Action").build();
        Genre drama = Genre.builder().id(2L).name("Drama").build();
        Genre comedy = Genre.builder().id(3L).name("Comedy").build();

        when(genreRepository.findAll()).thenReturn(List.of(action, drama, comedy));

        List<GenreDto> result = genreService.getAllGenres();

        assertThat(result).hasSize(3);

        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Action");

        assertThat(result.get(1).getId()).isEqualTo(2L);
        assertThat(result.get(1).getName()).isEqualTo("Drama");

        assertThat(result.get(2).getId()).isEqualTo(3L);
        assertThat(result.get(2).getName()).isEqualTo("Comedy");

        verify(genreRepository).findAll();
    }

    @Test
    @DisplayName("getAllGenres: empty repository → returns empty list")
    void getAllGenres_emptyRepository_returnsEmptyList() {
        when(genreRepository.findAll()).thenReturn(List.of());

        List<GenreDto> result = genreService.getAllGenres();

        assertThat(result).isEmpty();
        verify(genreRepository).findAll();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // createGenre tests
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createGenre: new name → saves genre and returns GenreDto with assigned id")
    void createGenre_newName_saved() {
        GenreDto request = GenreDto.builder().name("Horror").build();

        Genre savedGenre = Genre.builder().id(4L).name("Horror").build();

        when(genreRepository.findByName("Horror")).thenReturn(Optional.empty());
        when(genreRepository.save(any(Genre.class))).thenReturn(savedGenre);

        GenreDto result = genreService.createGenre(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(4L);
        assertThat(result.getName()).isEqualTo("Horror");

        ArgumentCaptor<Genre> genreCaptor = ArgumentCaptor.forClass(Genre.class);
        verify(genreRepository).save(genreCaptor.capture());
        assertThat(genreCaptor.getValue().getName()).isEqualTo("Horror");
    }

    @Test
    @DisplayName("createGenre: duplicate name → throws AlreadyExistsException and does not save")
    void createGenre_duplicateName_throwsAlreadyExists() {
        GenreDto request = GenreDto.builder().name("Action").build();

        Genre existingGenre = Genre.builder().id(1L).name("Action").build();
        when(genreRepository.findByName("Action")).thenReturn(Optional.of(existingGenre));

        assertThatThrownBy(() -> genreService.createGenre(request))
                .isInstanceOf(AlreadyExistsException.class)
                .hasMessageContaining("Action");

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

        assertThat(result.getName()).isEqualTo("Thriller");
        assertThat(result.getId()).isEqualTo(5L);
    }
}
