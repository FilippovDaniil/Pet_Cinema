package com.cinema.hall.config;

import com.cinema.hall.entity.*;
import com.cinema.hall.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// @Slf4j — Lombok генерирует поле: private static final Logger log = LoggerFactory.getLogger(DataLoader.class)
// Позволяет использовать log.info(), log.warn() и т.д. без ручного объявления логгера.
@Slf4j
// @Component — Spring создаёт DataLoader как управляемый бин.
// CommandLineRunner выполняется ОДИН РАЗ при старте Spring Context (после инициализации всех бинов).
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final HallRepository hallRepository;
    private final ExtraServiceRepository extraServiceRepository;
    private final SessionRepository sessionRepository;

    // run() вызывается Spring автоматически после старта контекста.
    // Аргументы args — аргументы командной строки (не используются).
    //
    // @Transactional — всё в одной транзакции. Если что-то пойдёт не так — откат (rollback).
    // Гарантирует атомарность: либо все данные вставятся, либо ничего.
    @Override
    @Transactional
    public void run(String... args) {
        // Идемпотентная проверка: если залы уже созданы — пропускаем загрузку.
        // Защищает от дублирования данных при перезапуске сервиса.
        if (hallRepository.count() > 0) {
            log.info("Data already loaded, skipping DataLoader.");
            return;
        }
        loadData();
    }

    private void loadData() {
        log.info("Loading initial data for hall-service...");

        // ── Зал 1: Стандартный ──────────────────────────────────────────────
        Hall hall1 = hallRepository.save(Hall.builder()
                .name("Зал 1")
                .type(HallType.NORMAL)
                .rowsCount(10)        // 10 рядов
                .seatsPerRow(15)      // 15 мест в ряду = 150 мест всего
                .description("Стандартный зал с удобными креслами")
                .build());

        // ── Зал 2: VIP с доп.услугами ───────────────────────────────────────
        Hall hall2 = hallRepository.save(Hall.builder()
                .name("Зал VIP")
                .type(HallType.VIP)
                .rowsCount(8)         // Меньше рядов — премиальный формат
                .seatsPerRow(10)      // 80 мест
                .description("VIP-зал с премиальным обслуживанием")
                .build());

        // Доп.услуги VIP-зала — привязаны к hall2
        extraServiceRepository.save(ExtraService.builder()
                .hall(hall2)
                .name("Вибрация кресла")
                .price(new BigDecimal("50.00")) // 50 руб
                .build());
        extraServiceRepository.save(ExtraService.builder()
                .hall(hall2)
                .name("Персональный официант")
                .price(new BigDecimal("100.00")) // 100 руб
                .build());

        // ── Зал 3: 3D ───────────────────────────────────────────────────────
        Hall hall3 = hallRepository.save(Hall.builder()
                .name("Зал 3D")
                .type(HallType.THREE_D)
                .rowsCount(12)        // 240 мест — самый вместительный
                .seatsPerRow(20)
                .description("Зал с современным 3D-оборудованием")
                .build());

        extraServiceRepository.save(ExtraService.builder()
                .hall(hall3)
                .name("3D-очки премиум")
                .price(new BigDecimal("30.00"))
                .build());

        // ── Зал 4: 5D с эффектами ───────────────────────────────────────────
        Hall hall4 = hallRepository.save(Hall.builder()
                .name("Зал 5D")
                .type(HallType.FIVE_D)
                .rowsCount(6)         // Самый маленький — специальные кресла дороги
                .seatsPerRow(12)      // 72 места
                .description("Захватывающий 5D-зал с эффектами присутствия")
                .build());

        extraServiceRepository.save(ExtraService.builder()
                .hall(hall4)
                .name("Обливание водой")
                .price(new BigDecimal("40.00"))
                .build());
        extraServiceRepository.save(ExtraService.builder()
                .hall(hall4)
                .name("Ветродуй")
                .price(new BigDecimal("40.00"))
                .build());

        log.info("Created 4 halls with extra services");

        // ── Генерация сеансов на следующие 7 дней ───────────────────────────
        List<Hall> halls = List.of(hall1, hall2, hall3, hall4);

        // movieIds: фильмы из movie-service (DataLoader в movie-service создаёт 5 фильмов с id 1-5).
        // Сеансы распределяются по фильмам циклически (movieIndex % 5).
        long[] movieIds = {1L, 2L, 3L, 4L, 5L};

        // Длительности фильмов в минутах (для расчёта endTime = startTime + duration).
        int[] movieDurations = {122, 169, 95, 180, 100};

        // Базовые цены: индексируются по hallIndex (а не movieIndex)!
        // Цена зависит от типа зала, а не от фильма.
        BigDecimal[] basePrices = {
                new BigDecimal("300.00"),  // Зал 1: NORMAL
                new BigDecimal("350.00"),  // Зал VIP
                new BigDecimal("500.00"),  // Зал 3D
                new BigDecimal("700.00")   // Зал 5D
        };

        int movieIndex = 0;                   // Счётчик для циклического перебора фильмов
        LocalDate today = LocalDate.now();    // Сегодняшняя дата (сеансы с сегодня)
        List<Session> sessions = new ArrayList<>();

        // Внешний цикл: 7 дней (день 0 = сегодня, день 6 = через 6 дней)
        for (int day = 0; day < 7; day++) {
            LocalDate date = today.plusDays(day); // Дата текущего дня цикла

            // Внутренний цикл: 4 зала
            for (int hallIndex = 0; hallIndex < halls.size(); hallIndex++) {
                Hall hall = halls.get(hallIndex);
                long movieId = movieIds[movieIndex % movieIds.length]; // Циклический выбор фильма
                int duration = movieDurations[movieIndex % movieDurations.length];
                BigDecimal price = basePrices[hallIndex]; // Цена по залу (не по фильму)

                // Сеанс в 12:00 для текущего зала и фильма
                LocalDateTime start12 = date.atTime(12, 0); // date + 12:00:00
                sessions.add(Session.builder()
                        .movieId(movieId)
                        .hall(hall)
                        .startTime(start12)
                        .endTime(start12.plusMinutes(duration)) // 12:00 + длительность фильма
                        .basePrice(price)
                        .active(true)
                        .build());

                movieIndex++; // Переходим к следующему фильму для сеанса в 18:00

                movieId = movieIds[movieIndex % movieIds.length]; // Другой фильм для вечернего сеанса
                duration = movieDurations[movieIndex % movieDurations.length];

                // Сеанс в 18:00 — другой фильм в том же зале
                LocalDateTime start18 = date.atTime(18, 0);
                sessions.add(Session.builder()
                        .movieId(movieId)
                        .hall(hall)
                        .startTime(start18)
                        .endTime(start18.plusMinutes(duration)) // 18:00 + длительность фильма
                        .basePrice(price)
                        .active(true)
                        .build());

                movieIndex++; // Переходим к следующему фильму для следующего зала/дня
            }
        }

        // saveAll() — вставляет все сеансы одним batch-запросом (эффективнее N отдельных save()).
        // Итого: 7 дней × 4 зала × 2 сеанса = 56 сеансов
        sessionRepository.saveAll(sessions);
        log.info("Created {} sessions for the next 7 days", sessions.size());
        log.info("DataLoader finished successfully.");
    }
}
