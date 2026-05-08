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

@SpringBootTest(
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@ActiveProfiles("test")
@Testcontainers
class HallServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("hall_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @Autowired
    private HallService hallService;

    @Autowired
    private ExtraServiceService extraServiceService;

    @Autowired
    private SessionService sessionService;

    // ------------------------------------------------------------------ createHall_thenGet_verifyPersisted

    @Test
    void createHall_thenGet_verifyPersisted() {
        HallCreateRequest request = HallCreateRequest.builder()
                .name("Integration Hall")
                .type("VIP")
                .rowsCount(8)
                .seatsPerRow(12)
                .description("Integration test hall")
                .build();

        HallDto created = hallService.createHall(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Integration Hall");
        assertThat(created.getType()).isEqualTo("VIP");

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
        HallCreateRequest hallRequest = HallCreateRequest.builder()
                .name("VIP Hall ES")
                .type("VIP")
                .rowsCount(4)
                .seatsPerRow(6)
                .description("vip desc")
                .build();
        HallDto hall = hallService.createHall(hallRequest);

        ExtraServiceCreateRequest es1 = ExtraServiceCreateRequest.builder()
                .name("Champagne").price(new BigDecimal("25.00")).build();
        ExtraServiceCreateRequest es2 = ExtraServiceCreateRequest.builder()
                .name("Caviar").price(new BigDecimal("40.00")).build();

        extraServiceService.addExtraService(hall.getId(), es1);
        extraServiceService.addExtraService(hall.getId(), es2);

        List<ExtraServiceDto> services = extraServiceService.getExtraServicesByHallId(hall.getId());

        assertThat(services).hasSize(2);
        assertThat(services).extracting(ExtraServiceDto::getName)
                .containsExactlyInAnyOrder("Champagne", "Caviar");
        assertThat(services).allMatch(s -> s.getHallId().equals(hall.getId()));
    }

    // ------------------------------------------------------------------ createSession_thenGetById

    @Test
    void createSession_thenGetById_fieldsCorrect() {
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
                .movieId(101L)
                .hallId(hall.getId())
                .startTime(start)
                .endTime(end)
                .basePrice(new BigDecimal("18.50"))
                .build();

        SessionDto created = sessionService.createSession(sessionRequest);

        assertThat(created.getId()).isNotNull();
        assertThat(created.isActive()).isTrue();

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
        HallCreateRequest request = HallCreateRequest.builder()
                .name("Temp Hall")
                .type("NORMAL")
                .rowsCount(3)
                .seatsPerRow(5)
                .build();
        HallDto hall = hallService.createHall(request);
        Long hallId = hall.getId();

        hallService.deleteHall(hallId);

        assertThatThrownBy(() -> hallService.getHallById(hallId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(hallId));
    }
}
