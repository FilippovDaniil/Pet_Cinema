package com.cinema.hall;

import com.cinema.dto.hall.ExtraServiceCreateRequest;
import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.HallCreateRequest;
import com.cinema.dto.hall.HallDto;
import com.cinema.dto.hall.SessionCreateRequest;
import com.cinema.dto.hall.SessionDto;
import com.cinema.hall.exception.ResourceNotFoundException;
import com.cinema.hall.service.ExtraServiceService;
import com.cinema.hall.service.HallService;
import com.cinema.hall.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @SpringBootTest — поднимает ПОЛНЫЙ Spring Context (сервисы, репозитории, JPA, Security).
// Параметры properties: отключаем Eureka и Discovery чтобы не пытаться подключиться к реальному реестру.
// Kafka в hall-service нет → не нужно отключать KafkaAutoConfiguration.
@SpringBootTest(
        properties = {
                "eureka.client.enabled=false",         // Не регистрируемся в Eureka при тестах
                "spring.cloud.discovery.enabled=false" // Не используем discovery для lb://
        }
)
// @ActiveProfiles("test") — загружает application-test.yml поверх application.yml.
// Переопределяет: datasource.url (Testcontainers), ddl-auto (create-drop), eureka.enabled=false,
// management.health.redis.enabled=false (чтобы Redis healthcheck не падал).
@ActiveProfiles("test")
// @Testcontainers — активирует JUnit 5 расширение Testcontainers.
// Управляет жизненным циклом @Container полей: start() до тестов, stop() после.
@Testcontainers
class HallServiceIntegrationTest {

    // @Container static — один контейнер PostgreSQL на ВЕСЬ класс (все тесты используют его).
    // Статический = контейнер запускается один раз (не пересоздаётся для каждого теста).
    // Нестатический @Container = новый контейнер на каждый тест (изоляция, но медленнее).
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("hall_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    // @DynamicPropertySource — подставляет параметры Testcontainers в Spring Environment.
    // Вызывается ПЕРЕД созданием ApplicationContext (поэтому static метод).
    // Переопределяет spring.datasource.url из application-test.yml на реальный URL контейнера.
    // Необходимо потому что порт PostgreSQL в Testcontainers ДИНАМИЧЕСКИЙ (случайный).
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);     // jdbc:postgresql://localhost:PORT/hall_db_test
        registry.add("spring.datasource.username", postgres::getUsername); // "cinema"
        registry.add("spring.datasource.password", postgres::getPassword); // "cinema"
        // Явно задаём стандартный драйвер (переопределяем TC-драйвер из application-test.yml если задан)
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    // Redis нет в тестовом окружении (не запускаем контейнер Redis).
    // @MockBean RedisConnectionFactory — замещает реальный factory Mockito-моком.
    // Без этого Spring пытается подключиться к localhost:6379 и падает с ConnectionRefusedException.
    //
    // Важно: hall-service имеет ДВА типа RedisConnectionFactory:
    //   1. RedisConnectionFactory — синхронный (для StringRedisTemplate в RedisConfig)
    //   2. ReactiveRedisConnectionFactory — реактивный (Spring Boot автоконфигурация создаёт его
    //      даже если мы его явно не используем, если есть spring-boot-starter-data-redis)
    // Мокируем оба чтобы избежать ConnectionRefusedException.
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    // Реальные сервисы из Spring Context — тестируем их с реальной PostgreSQL (в контейнере)
    @Autowired
    private HallService hallService;

    @Autowired
    private ExtraServiceService extraServiceService;

    @Autowired
    private SessionService sessionService;

    // ------------------------------------------------------------------ createHall_thenGet_verifyPersisted

    @Test
    void createHall_thenGet_verifyPersisted() {
        // Создаём зал через сервис → данные идут в реальную PostgreSQL (Testcontainers)
        HallCreateRequest request = HallCreateRequest.builder()
                .name("Integration Hall")
                .type("VIP")
                .rowsCount(8)
                .seatsPerRow(12)
                .description("Integration test hall")
                .build();

        HallDto created = hallService.createHall(request);

        // id назначен PostgreSQL (не null) → INSERT прошёл успешно
        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Integration Hall");
        assertThat(created.getType()).isEqualTo("VIP");

        // Читаем из БД по id → проверяем персистентность
        HallDto fetched = hallService.getHallById(created.getId());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getName()).isEqualTo("Integration Hall");
        assertThat(fetched.getType()).isEqualTo("VIP");
        assertThat(fetched.getRowsCount()).isEqualTo(8);
        assertThat(fetched.getSeatsPerRow()).isEqualTo(12);
        assertThat(fetched.getDescription()).isEqualTo("Integration test hall");
    }

    // ------------------------------------------------------------------ addExtraService_toVipHall

    @Test
    void addExtraService_toVipHall_returnsCorrectList() {
        // Создаём зал — нужен для FK связи ExtraService.hall_id
        HallCreateRequest hallRequest = HallCreateRequest.builder()
                .name("VIP Hall ES")
                .type("VIP")
                .rowsCount(4)
                .seatsPerRow(6)
                .description("vip desc")
                .build();
        HallDto hall = hallService.createHall(hallRequest);

        // Добавляем 2 доп.услуги к залу
        ExtraServiceCreateRequest es1 = ExtraServiceCreateRequest.builder()
                .name("Champagne").price(new BigDecimal("25.00")).build();
        ExtraServiceCreateRequest es2 = ExtraServiceCreateRequest.builder()
                .name("Caviar").price(new BigDecimal("40.00")).build();

        extraServiceService.addExtraService(hall.getId(), es1);
        extraServiceService.addExtraService(hall.getId(), es2);

        // Получаем список услуг зала из БД
        List<ExtraServiceDto> services = extraServiceService.getExtraServicesByHallId(hall.getId());

        assertThat(services).hasSize(2);
        // containsExactlyInAnyOrder — все элементы присутствуют, порядок не важен
        assertThat(services).extracting(ExtraServiceDto::getName)
                .containsExactlyInAnyOrder("Champagne", "Caviar");
        // allMatch — все услуги принадлежат нашему залу (FK корректен)
        assertThat(services).allMatch(s -> s.getHallId().equals(hall.getId()));
    }

    // ------------------------------------------------------------------ createSession_thenGetById

    @Test
    void createSession_thenGetById_fieldsCorrect() {
        // Создаём зал для сеанса
        HallCreateRequest hallRequest = HallCreateRequest.builder()
                .name("Session Hall")
                .type("THREE_D")
                .rowsCount(10)
                .seatsPerRow(20)
                .description("3D session hall")
                .build();
        HallDto hall = hallService.createHall(hallRequest);

        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 14, 0);
        LocalDateTime end   = LocalDateTime.of(2026, 9, 1, 16, 0);

        SessionCreateRequest sessionRequest = SessionCreateRequest.builder()
                .movieId(101L)         // movieId из другого сервиса — просто Long
                .hallId(hall.getId())  // hallId из той же БД
                .startTime(start)
                .endTime(end)
                .basePrice(new BigDecimal("18.50"))
                .build();

        SessionDto created = sessionService.createSession(sessionRequest);

        assertThat(created.getId()).isNotNull(); // id назначен PostgreSQL
        assertThat(created.isActive()).isTrue(); // Новый сеанс активен

        // Читаем из БД и проверяем все поля
        SessionDto fetched = sessionService.getSessionById(created.getId());

        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getMovieId()).isEqualTo(101L);
        assertThat(fetched.getHallId()).isEqualTo(hall.getId());
        assertThat(fetched.getStartTime()).isEqualTo(start);
        assertThat(fetched.getEndTime()).isEqualTo(end);
        assertThat(fetched.getBasePrice()).isEqualByComparingTo("18.50");
        assertThat(fetched.isActive()).isTrue();
    }

    // ------------------------------------------------------------------ deleteHall_thenVerify

    @Test
    void deleteHall_thenGetById_throwsResourceNotFoundException() {
        // Создаём временный зал
        HallCreateRequest request = HallCreateRequest.builder()
                .name("Temp Hall")
                .type("NORMAL")
                .rowsCount(3)
                .seatsPerRow(5)
                .build();
        HallDto hall = hallService.createHall(request);
        Long hallId = hall.getId();

        // Удаляем зал
        hallService.deleteHall(hallId);

        // Пытаемся получить удалённый зал → должны получить 404
        assertThatThrownBy(() -> hallService.getHallById(hallId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(hallId));
    }
}
