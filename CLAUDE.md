# CLAUDE.md — Cinema System: полный контекст проекта

## Что это за проект

Многомодульная микросервисная система управления кинотеатром. Учебный проект, реализованный по подробному ТЗ. Цель — показать полный стек современной Java-разработки: микросервисы, Kafka, Redis, JWT, Docker, React.

Директория: `C:\Users\MaxxPC\IdeaProjects\Pet_Cinema`

---

## Структура модулей Gradle (Kotlin DSL)

```
cinema-system/
├── settings.gradle.kts        ← регистрирует все подпроекты
├── build.gradle.kts           ← общая конфигурация (Spring Boot BOM, Lombok, Actuator)
├── common-dtos/               ← общие DTO для всех сервисов
├── service-discovery/         ← Eureka Server (порт 8761)
├── api-gateway/               ← Spring Cloud Gateway (порт 8080)
├── auth-service/              ← JWT аутентификация (порт 8081)
├── movie-service/             ← фильмы, жанры, отзывы (порт 8082)
├── hall-service/              ← залы, доп.услуги, сеансы (порт 8083)
├── order-service/             ← заказы, билеты, меню (порт 8084)
├── support-service/           ← чат техподдержки (порт 8085)
├── notification-service/      ← уведомления через Kafka (порт 8086)
├── payment-simulator/         ← имитация платёжного шлюза (порт 8087)
├── frontend/                  ← React 18 + TypeScript + Vite (порт 80)
├── infrastructure/
│   ├── loki/loki-config.yml
│   ├── promtail/promtail-config.yml
│   └── grafana/provisioning/
└── docker-compose.yml
```

---

## Java-пакеты по сервисам

| Сервис | Пакет |
|--------|-------|
| common-dtos | `com.cinema.dto.*` |
| service-discovery | `com.cinema.discovery` |
| api-gateway | `com.cinema.gateway` |
| auth-service | `com.cinema.auth` |
| movie-service | `com.cinema.movie` |
| hall-service | `com.cinema.hall` |
| order-service | `com.cinema.order` |
| support-service | `com.cinema.support` |
| notification-service | `com.cinema.notification` |
| payment-simulator | `com.cinema.payment` |

---

## Стек технологий

- **Java 17**, **Spring Boot 3.2.5**, **Spring Cloud 2023.0.1**
- **Gradle Kotlin DSL** — многомодульный проект
- **PostgreSQL 15** — по одной БД на каждый сервис (cinema/cinema)
- **Redis 7** — кеш + blacklist токенов
- **Apache Kafka** (confluentinc/cp-kafka:7.6.0) + Zookeeper
- **Spring Cloud Gateway** — API Gateway с JWT фильтром
- **Netflix Eureka** — service discovery
- **Spring Security + jjwt 0.12.5** — JWT аутентификация
- **Loki + Grafana + promtail** — сбор и визуализация логов
- **React 18 + TypeScript + Vite** — фронтенд
- **Nginx** — раздача React билда в Docker

---

## common-dtos: пакеты и классы

Все DTO в `common-dtos/src/main/java/com/cinema/dto/`:

- `auth/` — AuthRequest, AuthResponse, RegisterRequest, RefreshRequest, UserDto
- `movie/` — MovieDto, MovieCreateRequest, ReviewDto, ReviewCreateRequest, CommentDto, CommentCreateRequest, GenreDto
- `hall/` — HallDto, HallCreateRequest, ExtraServiceDto, ExtraServiceCreateRequest, SessionDto, SessionCreateRequest
- `order/` — OrderDto, OrderItemDto, TicketOrderRequest, SellerTicketOrderRequest, FoodOrderRequest (+FoodOrderItemRequest), FoodItemDto, TicketDto, PaymentWebhookRequest
- `support/` — SupportTicketDto, SupportTicketCreateRequest, SupportMessageDto, SupportMessageRequest, AssignAdminRequest
- `notification/` — NotificationDto
- `event/` — TicketPurchaseEvent, SupportMessageEvent, MovieUpdateEvent, PaymentRequestEvent
- `common/` — ErrorResponse, PageResponse<T>

Все классы используют Lombok: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.

---

## auth-service: архитектура

### Сущности
- `User` — id, username(unique), email(unique), password(BCrypt), role(Role enum), createdAt, updatedAt
- `RefreshToken` — id, token(text), user(ManyToOne), expiryDate, revoked(boolean)
- `Role` enum — ROLE_CLIENT, ROLE_SELLER, ROLE_ADMIN

### Логика токенов
- **Access token**: JWT, TTL 15 мин, claims: sub=userId, roles=[role]
- **Refresh token**: JWT, TTL 7 дней, хранится в БД + Redis blacklist при logout
- Генерация: `JwtUtils.generateAccessToken(User)`, `generateRefreshToken(User)`
- Ключ: `Keys.hmacShaKeyFor(jwtSecret.getBytes())`, jjwt 0.12.5 API

### Эндпоинты
- POST /api/auth/register — создание юзера
- POST /api/auth/login — возвращает {accessToken, refreshToken}
- POST /api/auth/refresh — ротация токенов (старый аннулируется)
- POST /api/auth/logout — refresh токен в Redis blacklist

### DataLoader создаёт при старте:
- client1 / password / ROLE_CLIENT
- seller1 / password / ROLE_SELLER
- admin1 / password / ROLE_ADMIN

### Redis
- Blacklist ключ: `blacklist:{token}`, TTL = оставшееся время жизни refresh токена
- Используется `StringRedisTemplate`

---

## movie-service: архитектура

### Сущности
- `Genre` — id, name(unique)
- `Movie` — id, title, description(TEXT), posterUrl, durationMinutes, type(MovieType), genres(ManyToMany с Genre)
- `MovieType` enum — TWO_D, THREE_D, FIVE_D
- `Review` — id, movieId(Long), userId(Long), rating(1-5), comment(TEXT), createdAt
- `Comment` — id, movieId(Long), userId(Long), text(TEXT), createdAt

### Redis кеш
- Ключ `movies:list:all`, TTL 10 мин — кеш списка фильмов
- При изменении фильма публикуется `MovieUpdateEvent` в Kafka топик `movie-update`
- api-gateway слушает `movie-update` и удаляет ключи `movies:list:*` из Redis

### Kafka
- Producer в топик `movie-update`: `{movieId, action: "CREATE"/"UPDATE"/"DELETE"}`

### Эндпоинты
- GET /api/movies?genre=&type=&durationMax=&page=&size= — публичный
- GET /api/movies/{id} — с отзывами и комментариями, средний рейтинг
- POST/PUT/DELETE /api/movies — только ADMIN
- POST /api/movies/{id}/reviews — только CLIENT (один отзыв на фильм)
- POST /api/movies/{id}/comments — только CLIENT

### DataLoader
5 фильмов: Звёздные войны(3D), Интерстеллар(2D), 3D Ужасы(3D), Аватар 5D(5D), Комедия дня(2D). 3 жанра: Action, Drama, Comedy.

---

## hall-service: архитектура

### Сущности
- `Hall` — id, name, type(HallType), rowsCount, seatsPerRow, description
- `HallType` enum — NORMAL, VIP, THREE_D, FIVE_D
- `ExtraService` — id, hall(ManyToOne Hall), name, price(BigDecimal)
- `Session` — id, movieId(Long), hall(ManyToOne Hall), startTime, endTime, basePrice(BigDecimal), active(boolean)

### Важно
- Session хранит movieId как Long (нет FK к movie-service — межсервисное разделение)
- order-service вызывает hall-service через Eureka: `lb://hall-service/api/sessions/{id}` для получения цены

### DataLoader
4 зала:
1. "Зал 1" — NORMAL, 10×15
2. "Зал VIP" — VIP, 8×10, услуги: "Вибрация кресла" 50р, "Персональный официант" 100р
3. "Зал 3D" — THREE_D, 12×20, услуга: "3D-очки премиум" 30р
4. "Зал 5D" — FIVE_D, 6×12, услуги: "Обливание водой" 40р, "Ветродуй" 40р
Сеансы: следующие 7 дней, 12:00 и 18:00, movieId 1-5 по кругу.

---

## order-service: архитектура

### Сущности
- `FoodItem` — id, name, price, category(FoodCategory)
- `FoodCategory` enum — DRINK, POPCORN, SNACK, OTHER
- `Order` — id, userId, sellerId(nullable), orderType(OrderType), status(OrderStatus), totalPrice, items(OneToMany), createdAt
- `OrderItem` — id, order(ManyToOne), itemType, ticketSessionId, ticketSeatRow, ticketSeatNumber, ticketExtraServices(JSON String), foodItem(ManyToOne), quantity, price
- `Ticket` — id, orderId, sessionId, userId, seatRow, seatNumber, extraServices(JSON), qrCode, status(TicketStatus)
- Enums: OrderType(TICKET/FOOD/MIXED), OrderStatus(PENDING/PAID/CANCELLED), ItemType(TICKET/FOOD), TicketStatus(ACTIVE/USED/CANCELLED)

### Поток оплаты (клиент-автомат)
1. POST /api/orders/ticket — создаётся Order(PENDING)
2. order-service публикует `PaymentRequestEvent` в Kafka топик `payment-request`
3. payment-simulator получает событие, ждёт 3 сек, POST /api/orders/webhook/payment {orderId, status:"SUCCESS"}
4. Order обновляется до PAID, создаётся Ticket
5. Публикуется `TicketPurchaseEvent` в Kafka `ticket-purchase`
6. notification-service создаёт уведомление пользователю

### Поток продавца
- POST /api/orders/ticket/by-seller — сразу PAID, Ticket создаётся немедленно
- POST /api/orders/food — PAID сразу

### Межсервисные вызовы
- `@LoadBalanced RestTemplate` → `lb://hall-service/api/sessions/{id}` для получения basePrice
- `lb://hall-service/api/halls/{hallId}/extra-services` для цен доп.услуг
- Отдельный `plainRestTemplate` (без @LoadBalanced) для self-call вебхука (если нужен)

### DataLoader: меню
Попкорн 250р, Кола 150р, Начос 200р, Вода 80р, Хот-дог 180р, Капкейк 120р

---

## support-service: архитектура

### Сущности
- `SupportTicket` — id, clientId(Long), adminId(Long nullable), subject, status(OPEN/CLOSED), createdAt, updatedAt
- `SupportMessage` — id, ticket(ManyToOne), senderId(Long), content(TEXT), sentAt

### Kafka
- Публикует `SupportMessageEvent` в топик `support-message`
- Событие содержит recipientId: если отправитель CLIENT → recipientId = adminId; если ADMIN → clientId
- notification-service создаёт уведомление получателю

### DataLoader
Демо-тикет от userId=1 (client1) с темой "Помогите с оформлением заказа", статус OPEN, 2 сообщения.

---

## notification-service: архитектура

### Kafka consumers (String deserializer + ручной парсинг JSON через ObjectMapper)
- Топик `ticket-purchase` → уведомление о покупке билета
- Топик `support-message` → уведомление о новом сообщении в поддержке

### Эндпоинты
- GET /api/notifications — список уведомлений текущего пользователя
- PATCH /api/notifications/{id}/read — отметить прочитанным

---

## payment-simulator: архитектура

- Слушает Kafka топик `payment-request` (String deserializer)
- При получении: @Async sleep 3 сек → POST на `http://order-service:8084/api/orders/webhook/payment` с {orderId, status:"SUCCESS", transactionId:UUID}
- Конфигурируемый URL: `order.webhook-url` в application.yml

---

## api-gateway: архитектура

### JWT фильтр (JwtAuthenticationFilter)
- Whitelist (без токена): /api/auth/register, /api/auth/login, /api/auth/refresh, /api/orders/webhook/**
- Остальные: проверяет Bearer токен через JwtUtils
- При невалидном токене → 401

### Kafka consumer (CacheInvalidationConsumer)
- Слушает `movie-update`
- Удаляет ключи `movies:list:*` из Redis через ReactiveRedisTemplate

### Маршруты
```
/api/auth/**           → lb://auth-service
/api/movies/**         → lb://movie-service
/api/genres/**         → lb://movie-service
/api/halls/**          → lb://hall-service
/api/sessions/**       → lb://hall-service
/api/orders/**         → lb://order-service
/api/food-menu/**      → lb://order-service
/api/support/**        → lb://support-service
/api/notifications/**  → lb://notification-service
```

---

## JWT: единый секрет

Все сервисы используют один и тот же секрет (env var `JWT_SECRET`):
```
mySecretKey12345678901234567890123456789012345678901234567890
```
Каждый сервис самостоятельно валидирует токен через свой `JwtUtils.java` (код идентичный во всех сервисах).

---

## Безопасность: паттерн во всех сервисах

Каждый сервис имеет:
1. `security/JwtUtils.java` — валидация токена, извлечение userId и ролей
2. `filter/JwtAuthFilter.java` (OncePerRequestFilter) — читает Authorization: Bearer, устанавливает SecurityContext
3. `config/SecurityConfig.java` — @Configuration @EnableWebSecurity, `SecurityFilterChain` bean, stateless сессия

---

## Kafka: топики и события

| Топик | Publisher | Consumer | Событие |
|-------|-----------|----------|---------|
| `ticket-purchase` | order-service | notification-service | {orderId, userId, movieTitle, sessionTime, totalPrice} |
| `support-message` | support-service | notification-service | {ticketId, senderId, content, recipientId} |
| `movie-update` | movie-service | api-gateway | {movieId, action} |
| `payment-request` | order-service | payment-simulator | {orderId, userId, amount} |

---

## Redis: ключи

| Ключ | Сервис | TTL | Назначение |
|------|--------|-----|-----------|
| `movies:list:all` | movie-service | 10 мин | Кеш списка фильмов |
| `sessions:movie:{movieId}` | movie-service | 5 мин | Кеш сеансов |
| `blacklist:{token}` | auth-service | до истечения refresh | Blacklist токенов |

---

## БД: подключение

Все сервисы используют одинаковые credentials:
- user: `cinema`, password: `cinema`
- Каждый сервис имеет свою БД: auth_db, movie_db, hall_db, order_db, support_db, notification_db
- `spring.jpa.hibernate.ddl-auto: update` — таблицы создаются автоматически

---

## Frontend: структура

```
frontend/src/
├── main.tsx                 ← точка входа React
├── index.css                ← глобальные стили (тёмная тема)
├── App.tsx                  ← BrowserRouter + Routes
├── types/index.ts           ← TypeScript интерфейсы
├── api/axios.ts             ← Axios + JWT interceptor + auto-refresh
├── context/AuthContext.tsx  ← глобальный стейт авторизации
├── components/Layout.tsx    ← навбар + Outlet
└── pages/
    ├── HomePage.tsx         ← афиша с фильтрами
    ├── MovieDetailPage.tsx  ← детали фильма, отзывы
    ├── SessionsPage.tsx     ← сеансы по фильму
    ├── BookingPage.tsx      ← выбор места, оплата
    ├── LoginPage.tsx
    ├── RegisterPage.tsx
    ├── ProfilePage.tsx      ← история заказов
    ├── SupportPage.tsx      ← чат поддержки (polling 5 сек)
    ├── AdminPage.tsx        ← 5-табовый дашборд
    └── SellerPage.tsx       ← продажа билетов/еды
```

### AuthContext
- Хранит `user` (id, role), `accessToken` в state
- `localStorage`: `accessToken`, `refreshToken`
- JWT декодируется вручную: `atob(token.split('.')[1])` — без сторонних библиотек
- Auto-refresh: Axios interceptor при 401 вызывает POST /api/auth/refresh

---

## Docker Compose: сервисы и depends_on

Порядок старта (через healthcheck):
1. Все PostgreSQL + Redis (healthcheck: pg_isready / redis-cli ping)
2. Zookeeper → Kafka (healthcheck: kafka-broker-api-versions)
3. service-discovery (Eureka, healthcheck: /actuator/health)
4. Все микросервисы (зависят от своей БД + Eureka + Kafka где нужно)
5. frontend (зависит от api-gateway)

Логирование: все сервисы логируют JSON в stdout → promtail собирает → Loki → Grafana.

---

## Сборка и запуск

```bash
# Собрать все JAR файлы
./gradlew build -x test

# Запустить всю систему
docker-compose up --build

# Пересобрать один сервис
./gradlew :auth-service:build -x test
docker-compose up --build auth-service
```

---

## Известные особенности и решения

1. **Дублирование JwtUtils** — каждый сервис имеет свой JwtUtils.java с идентичным кодом. Это намеренно (изоляция сервисов), но можно вынести в common-dtos если нужно.

2. **Session.movieId как Long** — hall-service не имеет FK на movie-service. Это правильная микросервисная изоляция.

3. **payment-simulator** — отдельный сервис имитирует внешний платёжный шлюз. Слушает Kafka `payment-request`, через 3 сек POST на order-service webhook.

4. **@LoadBalanced RestTemplate** — order-service имеет два бина RestTemplate: один с @LoadBalanced для lb://hall-service, другой `plainRestTemplate` для прямых HTTP вызовов.

5. **Kafka String десериализация** — notification-service и payment-simulator используют StringDeserializer и парсят JSON вручную через ObjectMapper для гибкости.

6. **Frontend proxy** — в режиме dev Vite проксирует /api → localhost:8080. В Docker Nginx проксирует /api → api-gateway:8080.

7. **JWT jti claim** — `JwtUtils.generateAccessToken()` и `generateRefreshToken()` добавляют `.id(UUID.randomUUID().toString())` в builder, чтобы токены, выданные в одну секунду, были уникальными (JWT `iat`/`exp` имеют точность до секунды по RFC 7519).

8. **Hibernate дублирование колонок** — в `OrderItem` и `SupportMessage` скалярное FK-поле (`Long foodItemId`, `Long ticketId`) аннотировано `@Column(name = "food_item_id")` / `@Column(name = "ticket_id")`. Без явного имени Hibernate создаёт два логических имени для одной физической колонки и падает с `DuplicateMappingException`.

9. **SecurityConfig + @EnableMethodSecurity** — в `order-service` и `support-service` добавлен handler для `org.springframework.security.access.AccessDeniedException` в `GlobalExceptionHandler`, иначе Spring Security кидает 500 вместо 403 при нарушении `@PreAuthorize`.

10. **SecurityConfig.exceptionHandling** — в `order-service` `SecurityConfig` явно задан `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)`, иначе Spring Security 6 по умолчанию возвращает 403 вместо 401 для неаутентифицированных запросов.

---

## Тесты: структура и паттерны

Тесты написаны во всех 6 бизнес-сервисах. Полный запуск: `./gradlew test` → BUILD SUCCESSFUL.

### Структура тестов в каждом сервисе
```
src/test/java/com/cinema/{service}/
├── controller/
│   ├── XxxControllerTest.java     ← @WebMvcTest, web layer only
│   └── ...
├── service/
│   ├── XxxServiceTest.java        ← @ExtendWith(MockitoExtension.class), pure unit
│   └── ...
└── XxxServiceIntegrationTest.java ← @SpringBootTest + Testcontainers (PostgreSQL)
```

### Паттерн @WebMvcTest

```java
@WebMvcTest(MyController.class)
@Import(SecurityConfig.class)      // обязательно — @WebMvcTest не сканирует @Configuration
class MyControllerTest {

    @MockBean
    private MyService myService;

    @MockBean
    private JwtUtils jwtUtils;     // нужен реальному JwtAuthFilter (найден сканом)
    // JwtAuthFilter НЕ мокируем — мок глотает все запросы (Mockito void по умолчанию ничего не делает)
```

Аутентификация в тестах — через `SecurityMockMvcRequestPostProcessors.authentication()`:
```java
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

private UsernamePasswordAuthenticationToken auth(String userId, String role) {
    return new UsernamePasswordAuthenticationToken(
            userId, null, List.of(new SimpleGrantedAuthority(role)));
}

mockMvc.perform(get("/api/resource")
        .with(authentication(auth("1", "ROLE_USER"))))
```

**Почему не `jwt()`**: требует `spring-security-oauth2-resource-server` в classpath, которого нет.

### Паттерн @SpringBootTest (Integration)

```java
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers
class MyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("xxx_db_test")
            .withUsername("cinema")
            .withPassword("cinema");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    // Kafka — мок чтобы не нужен брокер
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;
```

### Redis в интеграционных тестах

`RedisConnectionFactory` и `ReactiveRedisConnectionFactory` — разные интерфейсы, мокировать надо оба:

```java
@MockBean
private RedisConnectionFactory redisConnectionFactory;

@MockBean
private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
```

В `src/test/resources/application-test.yml` добавить:
```yaml
management:
  health:
    redis:
      enabled: false
```

### @BeforeEach порядок очистки БД

Удалять в порядке child → parent (FK constraints):
```java
@BeforeEach
void setUp() {
    ticketRepository.deleteAll();
    orderRepository.deleteAll();   // order_items ссылаются на food_items
    foodItemRepository.deleteAll();
}
```

### Gradle 9.1 + JUnit 5

В корневом `build.gradle.kts` обязательно:
```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
}
// В блоке dependencies subprojects:
"testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
```

### Spring Security 6 в тестах

- `@WebMvcTest` + `@Import(SecurityConfig.class)` — чтобы загрузить правила авторизации и `@EnableMethodSecurity`
- `@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)` — обязателен в `GlobalExceptionHandler`, иначе `@PreAuthorize` отказы → 500
- `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` — нужен в `SecurityConfig.exceptionHandling()`, иначе 401 для неаутентифицированных превращается в 403
