package com.cinema.hall.controller;

import com.cinema.dto.hall.ExtraServiceCreateRequest;
import com.cinema.dto.hall.ExtraServiceDto;
import com.cinema.dto.hall.HallCreateRequest;
import com.cinema.dto.hall.HallDto;
import com.cinema.hall.service.ExtraServiceService;
import com.cinema.hall.service.HallService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest — загружает только Web-слой: контроллеры, фильтры, конвертеры.
// НЕ загружает: репозитории, сервисы, JPA, Redis — только MVC-компоненты.
// Параметр HallController.class — тестируем только этот контроллер (остальные игнорируются).
// Преимущество: тесты очень быстрые (нет полного Spring Context).
@WebMvcTest(HallController.class)
// @Import(SecurityConfig.class) — ОБЯЗАТЕЛЬНО: @WebMvcTest не сканирует @Configuration классы.
// Без этого SecurityConfig не загрузится, JwtAuthFilter не будет создан,
// и запросы пройдут без аутентификации (тесты будут работать некорректно).
// SecurityConfig импортирует JwtAuthFilter через @RequiredArgsConstructor → нужен @MockBean JwtUtils.
@Import(com.cinema.hall.config.SecurityConfig.class)
class HallControllerTest {

    // MockMvc — инструмент для эмуляции HTTP-запросов к контроллеру без реального HTTP-сервера.
    // Запросы обрабатываются напрямую внутри JVM (быстро, удобно для тестирования).
    @Autowired
    private MockMvc mockMvc;

    // ObjectMapper — для сериализации объектов в JSON (тело запроса в тестах).
    @Autowired
    private ObjectMapper objectMapper;

    // @MockBean — заменяет реальный HallService Mockito-моком в Spring Context.
    // Без этого Spring не сможет создать HallController (у него @RequiredArgsConstructor зависимость).
    @MockBean
    private HallService hallService;

    @MockBean
    private ExtraServiceService extraServiceService;

    // JwtUtils мокируется потому что JwtAuthFilter (загруженный через SecurityConfig)
    // имеет @RequiredArgsConstructor зависимость от JwtUtils.
    // Без @MockBean JwtUtils Spring Context не запустится (нет реального бина).
    // Важно: JwtAuthFilter НЕ мокируем — нам нужен реальный фильтр для проверки авторизации.
    @MockBean
    private com.cinema.hall.security.JwtUtils jwtUtils;

    // ------------------------------------------------------------------ GET /api/halls

    @Test
    void getAllHalls_publicEndpoint_returns200WithList() throws Exception {
        HallDto hall1 = HallDto.builder().id(1L).name("Hall A").type("NORMAL")
                .rowsCount(10).seatsPerRow(20).description("desc").build();
        HallDto hall2 = HallDto.builder().id(2L).name("Hall B").type("VIP")
                .rowsCount(5).seatsPerRow(10).description("vip desc").build();

        when(hallService.getAllHalls()).thenReturn(List.of(hall1, hall2));

        // GET /api/halls — публичный эндпоинт (не требует авторизации)
        mockMvc.perform(get("/api/halls"))
                .andExpect(status().isOk())                         // HTTP 200
                .andExpect(jsonPath("$.length()").value(2))         // Массив из 2 элементов
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Hall A"))
                .andExpect(jsonPath("$[1].type").value("VIP"));     // $.jsonPath синтаксис
    }

    // ------------------------------------------------------------------ POST /api/halls

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN") // Устанавливает аутентифицированного пользователя с ролью ADMIN
    void createHall_withAdminRole_returns201() throws Exception {
        HallCreateRequest request = HallCreateRequest.builder()
                .name("New Hall").type("VIP").rowsCount(6).seatsPerRow(12).description("test").build();
        HallDto response = HallDto.builder().id(10L).name("New Hall").type("VIP")
                .rowsCount(6).seatsPerRow(12).description("test").build();

        when(hallService.createHall(any(HallCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/halls")
                        // .with(csrf()) — добавляет CSRF-токен в запрос.
                        // Spring Security в тестах ВКЛЮЧАЕТ CSRF по умолчанию (даже если SecurityConfig отключил).
                        // @WebMvcTest с SecurityConfig загружает реальный SecurityConfig, но Spring Test
                        // переопределяет настройки CSRF. csrf() из spring-security-test обходит это.
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))) // Сериализуем request в JSON
                .andExpect(status().isCreated()) // HTTP 201
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("New Hall"))
                .andExpect(jsonPath("$.type").value("VIP"));
    }

    @Test
    void createHall_withoutAuth_returns401Or403() throws Exception {
        // Без @WithMockUser — запрос выполняется анонимно.
        // SecurityConfig: POST /api/halls → authenticated() → отказ.
        HallCreateRequest request = HallCreateRequest.builder()
                .name("No Auth Hall").type("NORMAL").rowsCount(5).seatsPerRow(10).build();

        mockMvc.perform(post("/api/halls")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // 401 или 403 — зависит от конкретной конфигурации Spring Security.
                // Используем кастомную проверку вместо жёсткого andExpect(status().is4xxClientError()).
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status)
                            .isIn(401, 403); // Принимаем оба варианта — главное что доступ запрещён
                });
    }

    // ------------------------------------------------------------------ PUT /api/halls/{id}

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void updateHall_withAdminRole_returns200() throws Exception {
        HallCreateRequest request = HallCreateRequest.builder()
                .name("Updated Hall").type("THREE_D").rowsCount(8).seatsPerRow(15).description("upd").build();
        HallDto response = HallDto.builder().id(1L).name("Updated Hall").type("THREE_D")
                .rowsCount(8).seatsPerRow(15).description("upd").build();

        // eq(1L) — проверяем что id передан как 1L (не any())
        when(hallService.updateHall(eq(1L), any(HallCreateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/halls/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Hall"))
                .andExpect(jsonPath("$.type").value("THREE_D"));
    }

    // ------------------------------------------------------------------ DELETE /api/halls/{id}

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void deleteHall_withAdminRole_returns204() throws Exception {
        // doNothing() — мокируем void-метод (hallService.deleteHall() возвращает void)
        doNothing().when(hallService).deleteHall(1L);

        mockMvc.perform(delete("/api/halls/1").with(csrf()))
                .andExpect(status().isNoContent()); // HTTP 204 No Content
    }

    // ------------------------------------------------------------------ GET /api/halls/{id}/extra-services

    @Test
    void getExtraServices_publicEndpoint_returns200() throws Exception {
        ExtraServiceDto es = ExtraServiceDto.builder().id(5L).hallId(1L)
                .name("Popcorn").price(new BigDecimal("5.00")).build();

        when(extraServiceService.getExtraServicesByHallId(1L)).thenReturn(List.of(es));

        mockMvc.perform(get("/api/halls/1/extra-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Popcorn"))
                .andExpect(jsonPath("$[0].hallId").value(1));
    }

    // ------------------------------------------------------------------ POST /api/halls/{id}/extra-services

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void addExtraService_withAdminRole_returns201() throws Exception {
        ExtraServiceCreateRequest request = ExtraServiceCreateRequest.builder()
                .name("Blanket").price(new BigDecimal("3.00")).build();
        ExtraServiceDto response = ExtraServiceDto.builder().id(20L).hallId(1L)
                .name("Blanket").price(new BigDecimal("3.00")).build();

        when(extraServiceService.addExtraService(eq(1L), any(ExtraServiceCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/halls/1/extra-services")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()) // HTTP 201
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.name").value("Blanket"))
                .andExpect(jsonPath("$.hallId").value(1));
    }
}
