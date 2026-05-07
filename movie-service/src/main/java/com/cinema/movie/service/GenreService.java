package com.cinema.movie.service;

import com.cinema.dto.movie.GenreDto;
import com.cinema.movie.entity.Genre;
import com.cinema.movie.exception.AlreadyExistsException;
import com.cinema.movie.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GenreService {

    private final GenreRepository genreRepository;

    @Transactional(readOnly = true)
    public List<GenreDto> getAllGenres() {
        return genreRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public GenreDto createGenre(GenreDto request) {
        genreRepository.findByName(request.getName()).ifPresent(g -> {
            throw new AlreadyExistsException("Genre already exists: " + request.getName());
        });
        Genre genre = Genre.builder()
                .name(request.getName())
                .build();
        Genre saved = genreRepository.save(genre);
        return toDto(saved);
    }

    private GenreDto toDto(Genre genre) {
        return GenreDto.builder()
                .id(genre.getId())
                .name(genre.getName())
                .build();
    }
}
