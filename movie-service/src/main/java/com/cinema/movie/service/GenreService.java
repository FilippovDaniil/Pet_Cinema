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

// @Service — маркер слоя бизнес-логики; Spring создаёт singleton-бин.
// @RequiredArgsConstructor — Lombok: конструктор с genreRepository для инъекции зависимостей.
@Service
@RequiredArgsConstructor
public class GenreService {

    private final GenreRepository genreRepository; // Инъекция через конструктор

    // readOnly = true — оптимизация: Hibernate не отслеживает изменения сущностей (dirty checking),
    // PostgreSQL может использовать read-only транзакцию с более слабой блокировкой.
    // Используется для SELECT-операций, где данные не меняются.
    @Transactional(readOnly = true)
    public List<GenreDto> getAllGenres() {
        return genreRepository.findAll().stream() // Загружает все жанры из таблицы genres
                .map(this::toDto)                 // Конвертирует каждый Genre в GenreDto (метод ссылка)
                .collect(Collectors.toList());     // Собирает в List<GenreDto>
    }

    // @Transactional (без readOnly): транзакция на чтение + запись.
    // Spring автоматически делает COMMIT при успехе и ROLLBACK при исключении.
    @Transactional
    public GenreDto createGenre(GenreDto request) {
        // Проверяем: жанр с таким именем уже существует?
        // ifPresent() — если Optional содержит значение (жанр найден) → бросаем исключение.
        // Это защита от дублирования: в БД есть UNIQUE constraint, но лучше поймать раньше.
        genreRepository.findByName(request.getName()).ifPresent(g -> {
            throw new AlreadyExistsException("Genre already exists: " + request.getName());
        });

        // Создаём сущность через Builder (паттерн строитель от Lombok).
        // id не устанавливаем — его сгенерирует PostgreSQL при INSERT.
        Genre genre = Genre.builder()
                .name(request.getName())
                .build();

        Genre saved = genreRepository.save(genre); // INSERT INTO genres (name) VALUES (?)
        return toDto(saved); // Возвращаем DTO с присвоенным id
    }

    // Вспомогательный маппер: Genre (сущность из БД) → GenreDto (объект для API ответа).
    // Отделяем слой БД от слоя API: клиент не знает о внутренних деталях сущности.
    private GenreDto toDto(Genre genre) {
        return GenreDto.builder()
                .id(genre.getId())
                .name(genre.getName())
                .build();
    }
}
