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

@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final HallRepository hallRepository;
    private final ExtraServiceRepository extraServiceRepository;
    private final SessionRepository sessionRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (hallRepository.count() > 0) {
            log.info("Data already loaded, skipping DataLoader.");
            return;
        }
        loadData();
    }

    private void loadData() {
        log.info("Loading initial data for hall-service...");

        // Hall 1: Normal
        Hall hall1 = hallRepository.save(Hall.builder()
                .name("Зал 1")
                .type(HallType.NORMAL)
                .rowsCount(10)
                .seatsPerRow(15)
                .description("Стандартный зал с удобными креслами")
                .build());

        // Hall 2: VIP with extra services
        Hall hall2 = hallRepository.save(Hall.builder()
                .name("Зал VIP")
                .type(HallType.VIP)
                .rowsCount(8)
                .seatsPerRow(10)
                .description("VIP-зал с премиальным обслуживанием")
                .build());

        extraServiceRepository.save(ExtraService.builder()
                .hall(hall2)
                .name("Вибрация кресла")
                .price(new BigDecimal("50.00"))
                .build());
        extraServiceRepository.save(ExtraService.builder()
                .hall(hall2)
                .name("Персональный официант")
                .price(new BigDecimal("100.00"))
                .build());

        // Hall 3: 3D with extra service
        Hall hall3 = hallRepository.save(Hall.builder()
                .name("Зал 3D")
                .type(HallType.THREE_D)
                .rowsCount(12)
                .seatsPerRow(20)
                .description("Зал с современным 3D-оборудованием")
                .build());

        extraServiceRepository.save(ExtraService.builder()
                .hall(hall3)
                .name("3D-очки премиум")
                .price(new BigDecimal("30.00"))
                .build());

        // Hall 4: 5D with extra services
        Hall hall4 = hallRepository.save(Hall.builder()
                .name("Зал 5D")
                .type(HallType.FIVE_D)
                .rowsCount(6)
                .seatsPerRow(12)
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

        // Create sessions for next 7 days for each hall at 12:00 and 18:00
        List<Hall> halls = List.of(hall1, hall2, hall3, hall4);
        long[] movieIds = {1L, 2L, 3L, 4L, 5L};
        int[] movieDurations = {122, 169, 95, 180, 100};
        BigDecimal[] basePrices = {
                new BigDecimal("300.00"),
                new BigDecimal("350.00"),
                new BigDecimal("500.00"),
                new BigDecimal("700.00")
        };

        int movieIndex = 0;
        LocalDate today = LocalDate.now();
        List<Session> sessions = new ArrayList<>();

        for (int day = 0; day < 7; day++) {
            LocalDate date = today.plusDays(day);
            for (int hallIndex = 0; hallIndex < halls.size(); hallIndex++) {
                Hall hall = halls.get(hallIndex);
                long movieId = movieIds[movieIndex % movieIds.length];
                int duration = movieDurations[movieIndex % movieDurations.length];
                BigDecimal price = basePrices[hallIndex];

                // 12:00 session
                LocalDateTime start12 = date.atTime(12, 0);
                sessions.add(Session.builder()
                        .movieId(movieId)
                        .hall(hall)
                        .startTime(start12)
                        .endTime(start12.plusMinutes(duration))
                        .basePrice(price)
                        .active(true)
                        .build());

                movieIndex++;
                movieId = movieIds[movieIndex % movieIds.length];
                duration = movieDurations[movieIndex % movieDurations.length];

                // 18:00 session
                LocalDateTime start18 = date.atTime(18, 0);
                sessions.add(Session.builder()
                        .movieId(movieId)
                        .hall(hall)
                        .startTime(start18)
                        .endTime(start18.plusMinutes(duration))
                        .basePrice(price)
                        .active(true)
                        .build());

                movieIndex++;
            }
        }

        sessionRepository.saveAll(sessions);
        log.info("Created {} sessions for the next 7 days", sessions.size());
        log.info("DataLoader finished successfully.");
    }
}
