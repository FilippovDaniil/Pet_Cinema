# Cinema System — Полное руководство

> Учебный проект: микросервисная система управления кинотеатром.

---

## Содержание

1. [Что это и зачем](#1-что-это-и-зачем)
2. [Общая архитектура](#2-общая-архитектура)
3. [Как работает каждый компонент](#3-как-работает-каждый-компонент)
4. [Аутентификация и авторизация](#4-аутентификация-и-авторизация)
5. [Межсервисное взаимодействие](#5-межсервисное-взаимодействие)
6. [Kafka: события и очереди](#6-kafka-события-и-очереди)
7. [Redis: кеш и токены](#7-redis-кеш-и-токены)
8. [Поток покупки билета](#8-поток-покупки-билета)
9. [Фронтенд](#9-фронтенд)
10. [Логирование и мониторинг](#10-логирование-и-мониторинг)
11. [Как запустить проект](#11-как-запустить-проект)
12. [Структура базы данных](#12-структура-базы-данных)
13. [API: все эндпоинты](#13-api-все-эндпоинты)
14. [Структура проекта](#14-структура-проекта)

---

## 1. Что это и зачем

Cinema System — это учебный микросервисный проект, который демонстрирует:

- **Микросервисную архитектуру**: вместо одного большого приложения (монолита) система состоит из 8 независимых сервисов, каждый отвечает за свою область
- **Spring Cloud**: инструменты для построения распределённых систем (Gateway, Eureka, Feign)
- **Асинхронное общение**: сервисы общаются через Kafka, не ожидая ответа друг друга
- **JWT аутентификацию**: безопасность без хранения сессий на сервере
- **Контейнеризацию**: вся система запускается одной командой через Docker Compose

### Роли пользователей

| Роль | Что может делать |
|------|-----------------|
| **Клиент** (ROLE_CLIENT) | Смотреть афишу, покупать билеты через терминал, писать отзывы, обращаться в поддержку |
| **Продавец** (ROLE_SELLER) | Продавать билеты и еду клиентам лично, просматривать свои продажи |
| **Администратор** (ROLE_ADMIN) | Управлять фильмами/залами/сеансами/меню, отвечать в поддержке |

---

## 2. Общая архитектура

```
Браузер / Frontend (React)
         │
         ▼
   ┌─────────────┐
   │  API Gateway │  ← единая точка входа, порт 8080
   │  (порт 8080) │  ← проверяет JWT токены
   └──────┬───────┘
          │  маршрутизирует запросы
          │
    ┌─────┼──────────────────────────────┐
    │     │                              │
    ▼     ▼                    ▼         ▼
auth   movie               hall       order
:8081  :8082               :8083      :8084
    │                              │
    │     ▼                    ▼   │
  Redis  support            notif  payment-sim
        :8085               :8086  :8087
          │                    ▲
          └──── Kafka ─────────┘
                (события)

Eureka (порт 8761) — реестр, все сервисы регистрируются здесь
PostgreSQL — по одной БД на каждый сервис
Redis — кеш + хранение заблокированных токенов
```

### Почему так устроено?

**API Gateway** — все запросы с фронтенда идут не напрямую к сервисам, а через шлюз. Шлюз:
- Проверяет JWT токен (не нужно проверять в каждом сервисе)
- Маршрутизирует запрос к нужному сервису
- Может делать rate limiting, логировать трафик

**Eureka** — это "телефонный справочник" для сервисов. Когда order-service хочет позвонить hall-service, он не знает IP адрес hall-service (в Docker IP динамический). Вместо этого он спрашивает Eureka: "дай мне адрес hall-service" — и Eureka отвечает.

**Отдельные БД** — каждый сервис владеет своими данными. Order-service не может напрямую читать таблицы hall-service. Это гарантирует независимость сервисов.

---

## 3. Как работает каждый компонент

### service-discovery (Eureka) — порт 8761

Eureka — это реестр сервисов. При старте каждый микросервис отправляет Eureka своё имя и адрес. Когда один сервис хочет вызвать другой, он спрашивает Eureka вместо хардкода IP.

```
auth-service стартует → регистрируется в Eureka как "auth-service" на 172.18.0.5:8081
order-service стартует → регистрируется как "order-service" на 172.18.0.8:8084
Когда order-service вызывает hall-service:
  lb://hall-service → Eureka говорит: 172.18.0.7:8083
```

Веб-интерфейс Eureka: http://localhost:8761 — можно видеть все зарегистрированные сервисы.

---

### api-gateway (Spring Cloud Gateway) — порт 8080

**Задача**: принять запрос, проверить токен, переслать нужному сервису.

**Как работает JWT фильтр**:
```
Запрос GET /api/movies
  → JwtAuthenticationFilter проверяет: этот путь в whitelist? Нет.
  → Берёт заголовок Authorization: Bearer eyJ...
  → Проверяет подпись JWT ключом из JWT_SECRET
  → Если ок — пропускает запрос к movie-service
  → Если нет — возвращает 401 Unauthorized
```

**Публичные пути (без токена)**:
- POST /api/auth/register
- POST /api/auth/login
- POST /api/auth/refresh
- POST /api/orders/webhook/** (для платёжного шлюза)

**Инвалидация кеша Redis**:
Gateway подписан на Kafka топик `movie-update`. Когда admin изменяет фильм → movie-service публикует событие → Gateway удаляет ключи `movies:list:*` из Redis.

---

### auth-service — порт 8081

Отвечает за регистрацию, вход и управление токенами.

**Как работает регистрация**:
```
POST /api/auth/register {username, email, password}
  → проверить уникальность username и email
  → зашифровать пароль BCrypt (нельзя хранить пароли в открытом виде!)
  → сохранить User в auth_db
  → вернуть UserDto
```

**Как работает вход**:
```
POST /api/auth/login {username, password}
  → найти User по username
  → проверить пароль BCrypt.matches(input, stored)
  → сгенерировать access token (JWT, 15 мин)
  → сгенерировать refresh token (JWT, 7 дней)
  → сохранить refresh token в БД (таблица refresh_tokens)
  → вернуть {accessToken, refreshToken}
```

**Как работает обновление токена**:
```
POST /api/auth/refresh {refreshToken}
  → найти token в БД → проверить не истёк ли → проверить не revoked ли
  → проверить что токен не в Redis blacklist
  → сгенерировать новую пару токенов
  → старый refresh token → revoked=true в БД
  → вернуть новую пару
```

**Как работает выход**:
```
POST /api/auth/logout {refreshToken}
  → найти в БД → revoked=true
  → добавить в Redis blacklist с TTL до истечения срока токена
```

---

### movie-service — порт 8082

Управляет каталогом фильмов.

**Кеширование через Redis**:
```
GET /api/movies (список всех фильмов)
  → проверить Redis ключ "movies:list:all"
  → если есть → вернуть из кеша (быстро!)
  → если нет → запрос к PostgreSQL → сохранить в Redis TTL 10 мин → вернуть
```

При создании/редактировании/удалении фильма:
1. Сохранить в БД
2. Опубликовать `MovieUpdateEvent` в Kafka топик `movie-update`
3. API Gateway получит событие и удалит кеш из Redis

**Средний рейтинг** вычисляется на лету при запросе деталей фильма — берутся все отзывы из таблицы reviews, считается среднее.

---

### hall-service — порт 8083

Управляет залами и сеансами.

**Важный момент про изоляцию**: Session хранит `movieId` как обычное число (Long), а не как FK на другую таблицу. Hall-service не знает ничего о структуре movie-service. Это правильная микросервисная изоляция — сервисы не зависят от внутренней структуры друг друга.

**Доп.услуги** привязаны к залу. Для VIP, 3D и 5D залов можно добавить услуги с ценой. При покупке билета клиент выбирает нужные услуги — их стоимость добавляется к базовой цене билета.

---

### order-service — порт 8084

Самый сложный сервис. Обрабатывает покупку билетов, еды, подтверждение оплаты.

**Взаимодействие с hall-service**:
```java
// order-service вызывает hall-service для получения цены сеанса
restTemplate.getForObject("lb://hall-service/api/sessions/{id}", SessionDto.class, sessionId)
// lb:// означает load-balanced через Eureka
```

**Webhook**: специальный эндпоинт `/api/orders/webhook/payment` получает подтверждение от платёжной системы (в нашем случае — от payment-simulator).

---

### support-service — порт 8085

Система тикетов. Клиент создаёт обращение → пишет сообщения → администратор отвечает.

После каждого сообщения публикуется `SupportMessageEvent` в Kafka. notification-service получает событие и создаёт уведомление получателю.

---

### notification-service — порт 8086

Слушает Kafka и создаёт уведомления в БД. Пользователь может запросить свои уведомления через GET /api/notifications.

**Почему Kafka, а не прямой REST?**: Представь, что notification-service недоступен в момент покупки билета. С прямым REST-вызовом уведомление было бы потеряно. Kafka сохраняет событие в очереди — как только сервис восстановится, он обработает все накопившиеся события.

---

### payment-simulator — порт 8087

Имитирует внешний платёжный шлюз. В реальной системе это был бы Stripe, ЮKassa и т.д.

```
order-service публикует в Kafka "payment-request": {orderId:42, amount:500}
payment-simulator получает → ждёт 3 секунды (имитация обработки)
→ POST http://order-service:8084/api/orders/webhook/payment
  {orderId:42, status:"SUCCESS", transactionId:"uuid-..."}
order-service обновляет заказ → PAID → создаёт Ticket
```

---

## 4. Аутентификация и авторизация

### JWT (JSON Web Token)

JWT — это строка вида `header.payload.signature`. Payload содержит данные (claims):
```json
{
  "sub": "1",
  "roles": ["ROLE_CLIENT"],
  "iat": 1715000000,
  "exp": 1715000900
}
```

Подпись гарантирует, что токен не был изменён. Любой сервис, знающий секретный ключ (`JWT_SECRET`), может проверить подпись.

**Access token**: 15 минут. Используется в каждом запросе в заголовке `Authorization: Bearer <token>`.

**Refresh token**: 7 дней. Используется только для получения новой пары токенов. Хранится в БД и в localStorage браузера.

### Почему два токена?

Если бы был только один долгоживущий токен — при его краже злоумышленник имел бы доступ 7 дней. С двумя токенами:
- Access token живёт 15 мин → даже если украден, скоро устареет
- Refresh token хранится в БД → при logout помечается как отозванный (revoked)
- При подозрении на компрометацию — отзываем refresh token, доступ заблокирован через 15 мин

### Как Spring Security проверяет роли

Каждый сервис имеет `JwtAuthFilter` (OncePerRequestFilter):
```java
// Из токена извлекается userId и роль
// Создаётся объект аутентификации и помещается в SecurityContext
SecurityContextHolder.getContext().setAuthentication(
    new UsernamePasswordAuthenticationToken(userId, null, authorities)
);
```

Затем Spring Security проверяет аннотации:
```java
@PreAuthorize("hasRole('ADMIN')")  // или hasAuthority('ROLE_ADMIN')
public ResponseEntity<?> createMovie(...)
```

---

## 5. Межсервисное взаимодействие

### Синхронное (REST через Eureka)

Используется когда нужен ответ немедленно:

```java
// В order-service: получить информацию о сеансе из hall-service
@LoadBalanced  // говорит Spring обрабатывать lb:// через Eureka
RestTemplate restTemplate;

SessionDto session = restTemplate.getForObject(
    "lb://hall-service/api/sessions/" + sessionId,
    SessionDto.class
);
```

`lb://hall-service` — это специальный URL. `@LoadBalanced RestTemplate` распознаёт `lb://` и заменяет `hall-service` на реальный IP из Eureka.

### Асинхронное (через Kafka)

Используется для событий, когда не нужен немедленный ответ:

```java
// Публикация события в movie-service
kafkaTemplate.send("movie-update", new MovieUpdateEvent(movie.getId(), "UPDATE"));

// Подписка в api-gateway (другой JVM процесс!)
@KafkaListener(topics = "movie-update", groupId = "gateway-group")
public void handleMovieUpdate(String message) {
    // инвалидировать кеш Redis
}
```

---

## 6. Kafka: события и очереди

Apache Kafka — это распределённый брокер сообщений. Работает по принципу "издатель-подписчик" (pub-sub).

### Как это работает

```
Топик = именованная очередь сообщений
Продюсер = тот кто пишет в топик
Консюмер = тот кто читает из топика
```

**Наши топики**:

| Топик | Кто пишет | Кто читает | Когда |
|-------|-----------|------------|-------|
| `ticket-purchase` | order-service | notification-service | После успешной оплаты |
| `support-message` | support-service | notification-service | При отправке сообщения |
| `movie-update` | movie-service | api-gateway | При изменении фильма |
| `payment-request` | order-service | payment-simulator | При создании заказа |

### Почему не просто REST?

1. **Надёжность**: если notification-service упал, сообщения не теряются — Kafka хранит их, пока сервис не вернётся
2. **Развязка**: order-service не знает о существовании notification-service. Он просто пишет событие в топик.
3. **Масштабирование**: можно запустить несколько экземпляров notification-service — Kafka распределит нагрузку

---

## 7. Redis: кеш и токены

Redis — это in-memory хранилище ключ-значение. Работает в оперативной памяти, поэтому в тысячи раз быстрее PostgreSQL.

### Кеш фильмов

```
Клиент запрашивает список фильмов
  ↓
movie-service: проверяю Redis["movies:list:all"]
  → есть → вернуть за ~1 мс (из памяти!)
  → нет  → запрос к PostgreSQL (~10-50 мс) → сохранить в Redis TTL 10 мин → вернуть
```

**TTL (Time To Live)** — через 10 минут Redis автоматически удаляет ключ. Это гарантирует, что кеш не будет бесконечно устаревшим.

**Инвалидация**: при изменении фильма кеш удаляется принудительно, не ожидая TTL.

### Blacklist токенов

При выходе (logout) refresh токен добавляется в Redis:
```
SET blacklist:{tokenString} "revoked" EX 604800
     ↑ ключ                  ↑ значение  ↑ TTL = 7 дней
```

При следующей попытке использовать refresh токен:
```
GET blacklist:{tokenString} → "revoked" → запрос отклонён!
```

---

## 8. Поток покупки билета

Это самый важный сценарий. Рассмотрим детально:

### Покупка через терминал (клиент сам)

```
1. Клиент на фронтенде выбирает сеанс, место, доп.услуги
   POST /api/orders/ticket {sessionId:5, seatRow:3, seatNumber:7, extraServiceIds:[1,2]}
   (JWT: Bearer <accessToken клиента>)

2. API Gateway проверяет JWT → пропускает → пересылает в order-service

3. order-service:
   - Вызывает hall-service: "дай цену сеанса 5" → basePrice=300р
   - Вызывает hall-service: "дай услуги зала" → вибрация 50р, официант 100р
   - Считает: 300 + 50 + 100 = 450р
   - Создаёт Order(status=PENDING, total=450р)
   - Создаёт OrderItem(ticketSessionId=5, seatRow=3, seatNumber=7, extraServices="[1,2]")
   - Публикует в Kafka "payment-request": {orderId:42, userId:1, amount:450}
   - Возвращает OrderDto клиенту

4. payment-simulator (другой контейнер!) получает из Kafka:
   - Ждёт 3 секунды (имитация обработки банком)
   - POST http://order-service:8084/api/orders/webhook/payment
     {orderId:42, status:"SUCCESS", transactionId:"abc-123"}

5. order-service обрабатывает webhook:
   - Order(id=42).status = PAID
   - Создаёт Ticket(sessionId=5, userId=1, seatRow=3, seatNumber=7, qrCode="QR-42-abc")
   - Публикует в Kafka "ticket-purchase": {orderId:42, userId:1, movieTitle:"...", totalPrice:450}

6. notification-service получает из Kafka "ticket-purchase":
   - Создаёт Notification(userId=1, title="Билет куплен!", content="Ваш билет на ... приобретён")

7. Клиент может проверить: GET /api/notifications → видит своё уведомление
```

### Продажа через продавца

```
1. Продавец вводит clientId, выбирает сеанс/место/услуги
   POST /api/orders/ticket/by-seller {clientId:1, sessionId:5, seatRow:3, ...}
   (JWT: Bearer <accessToken продавца>)

2. order-service:
   - Считает цену так же
   - Создаёт Order(status=PAID сразу! — продавец принял деньги наличными)
   - sellerId = id продавца из JWT
   - Сразу создаёт Ticket
   - Публикует в Kafka "ticket-purchase"
   (Нет шага с payment-simulator — оплата офлайн)
```

---

## 9. Фронтенд

React-приложение с клиентским роутингом (SPA — Single Page Application).

### Как работает SPA роутинг

В обычном сайте каждый URL = отдельная HTML страница на сервере. В SPA:
- Сервер всегда возвращает один и тот же `index.html`
- React Router обрабатывает URL в браузере и рендерит нужный компонент
- Nginx настроен: `try_files $uri $uri/ /index.html` — любой URL возвращает index.html

### Хранение токенов

```typescript
// AuthContext.tsx
login(accessToken, refreshToken) {
  localStorage.setItem('accessToken', accessToken);   // живёт в браузере
  localStorage.setItem('refreshToken', refreshToken);
}
```

### Автоматическое обновление токена

```typescript
// api/axios.ts
api.interceptors.response.use(null, async (error) => {
  if (error.response?.status === 401) {
    // Access token истёк → пробуем обновить
    const response = await axios.post('/api/auth/refresh', {refreshToken});
    // Сохраняем новую пару → повторяем исходный запрос
    return api(error.config);
  }
});
```

### Декодирование JWT на фронтенде

JWT payload — это просто base64-encoded JSON. Браузер может его прочитать без ключа (только проверку подписи делает сервер):
```typescript
function decodeToken(token) {
  const base64 = token.split('.')[1]; // payload часть
  return JSON.parse(atob(base64));    // base64 → JSON
}
// Получаем: {sub: "1", roles: ["ROLE_CLIENT"], exp: 1715000900}
```

### Защищённые маршруты

```tsx
<Route path="/admin" element={
  <ProtectedRoute roles={['ROLE_ADMIN']}>
    <AdminPage />
  </ProtectedRoute>
} />

// ProtectedRoute: если нет токена → /login, если не та роль → главная
```

---

## 10. Логирование и мониторинг

### Как это устроено

```
Микросервис → логирует JSON в stdout
      ↓
Docker (stdout каждого контейнера)
      ↓
Promtail (агент) читает Docker логи
      ↓
Loki (хранилище логов)
      ↓
Grafana (визуализация, порт 3000)
```

### Формат логов

Все сервисы настроены на JSON логирование:
```json
{"timestamp":"2026-05-07 21:30:00","level":"INFO","service":"auth-service","message":"User client1 logged in"}
```

Grafana: http://localhost:3000 (admin/admin)
- Источник данных: Loki (уже настроен автоматически)
- Дашборд "Cinema System Logs" — показывает логи всех сервисов
- Фильтр по контейнеру, уровню логирования

---

## 11. Как запустить проект

### Предварительные требования

- **Docker Desktop** — запущен
- **8+ GB RAM** выделено Docker (Kafka + 9 JVM требуют памяти)
- JDK и Gradle **не нужны** — всё собирается внутри Docker

### Запуск

```bash
docker-compose up --build
```

При первом запуске Docker скачает базовые образы и скомпилирует каждый сервис (~5-10 минут). При повторных запусках слои кешируются — быстрее.

### Что происходит при сборке

Каждый Java-сервис собирается в два этапа внутри Docker:
1. **builder** — `eclipse-temurin:17-jdk-alpine` запускает `./gradlew :service:bootJar`, собирает fat JAR
2. **runtime** — `eclipse-temurin:17-jre-alpine` копирует только JAR (без JDK и исходников)

Frontend — multi-stage build: Node 20 → Nginx.

### Порядок старта (занимает ~3-5 минут после сборки образов)

1. PostgreSQL × 6, Redis — старт с healthcheck `pg_isready` / `redis-cli ping`
2. Zookeeper → Kafka — healthcheck `kafka-broker-api-versions`
3. Eureka — healthcheck `/actuator/health`
4. Все микросервисы — JVM startup + DDL
5. Frontend (Nginx)

Проверка готовности: открыть http://localhost:8761 — должны быть видны все 8 сервисов.

### Доступные адреса после старта

| Что | Адрес |
|-----|-------|
| Фронтенд (React) | http://localhost:80 |
| API Gateway | http://localhost:8080 |
| Eureka Dashboard | http://localhost:8761 |
| Grafana (логи) | http://localhost:3000 |
| Loki | http://localhost:3100 |
| Kafka (external) | localhost:29092 |

### Тестовые пользователи

| Логин | Пароль | Роль |
|-------|--------|------|
| client1 | password | Клиент |
| seller1 | password | Продавец |
| admin1 | password | Администратор |

### Остановка

```bash
docker-compose down           # остановить, данные сохраняются
docker-compose down -v        # остановить + удалить все данные (volumes)
```

### Пересборка одного сервиса после изменений

```bash
docker-compose up --build auth-service
```

### Просмотр логов конкретного сервиса

```bash
docker-compose logs auth-service -f   # -f = follow (стриминг)
docker-compose logs movie-service --tail=50
```

---

## 12. Структура базы данных

### auth_db

```sql
users
  id BIGINT PRIMARY KEY
  username VARCHAR UNIQUE NOT NULL
  email VARCHAR UNIQUE NOT NULL
  password VARCHAR NOT NULL        -- BCrypt хеш
  role VARCHAR NOT NULL            -- ROLE_CLIENT / ROLE_SELLER / ROLE_ADMIN
  created_at TIMESTAMP
  updated_at TIMESTAMP

refresh_tokens
  id BIGINT PRIMARY KEY
  token VARCHAR(1000) NOT NULL
  user_id BIGINT REFERENCES users(id)
  expiry_date TIMESTAMP NOT NULL
  revoked BOOLEAN DEFAULT FALSE
```

### movie_db

```sql
genres (id, name UNIQUE)

movies (id, title, description TEXT, poster_url,
        duration_minutes INT, type VARCHAR)  -- TWO_D / THREE_D / FIVE_D

movie_genres  -- связка многие-ко-многим
  movie_id → movies(id)
  genre_id → genres(id)

reviews (id, movie_id, user_id, rating INT 1-5, comment TEXT, created_at)

comments (id, movie_id, user_id, text TEXT, created_at)
```

### hall_db

```sql
halls (id, name, type VARCHAR, rows_count INT, seats_per_row INT, description)
      -- type: NORMAL / VIP / THREE_D / FIVE_D

extra_services (id, hall_id → halls(id), name, price DECIMAL)

sessions (id, movie_id BIGINT,  -- просто число, нет FK на movie-service!
          hall_id → halls(id),
          start_time TIMESTAMP, end_time TIMESTAMP,
          base_price DECIMAL, active BOOLEAN)
```

### order_db

```sql
food_items (id, name, price DECIMAL, category VARCHAR)
           -- DRINK / POPCORN / SNACK / OTHER

orders (id, user_id BIGINT, seller_id BIGINT,
        order_type VARCHAR,  -- TICKET / FOOD / MIXED
        status VARCHAR,      -- PENDING / PAID / CANCELLED
        total_price DECIMAL, created_at TIMESTAMP)

order_items (id, order_id → orders(id),
             item_type VARCHAR,  -- TICKET / FOOD
             -- для билета:
             ticket_session_id BIGINT, ticket_seat_row INT,
             ticket_seat_number INT, ticket_extra_services TEXT, -- JSON
             -- для еды:
             food_item_id → food_items(id), quantity INT,
             price DECIMAL)

tickets (id, order_id, session_id, user_id,
         seat_row INT, seat_number INT,
         extra_services TEXT,  -- JSON массив id услуг
         qr_code VARCHAR,
         status VARCHAR)  -- ACTIVE / USED / CANCELLED
```

### support_db

```sql
support_tickets (id, client_id BIGINT, admin_id BIGINT,
                 subject VARCHAR, status VARCHAR,  -- OPEN / CLOSED
                 created_at TIMESTAMP, updated_at TIMESTAMP)

support_messages (id, ticket_id → support_tickets(id),
                  sender_id BIGINT, content TEXT, sent_at TIMESTAMP)
```

### notification_db

```sql
notifications (id, user_id BIGINT, title VARCHAR,
               content TEXT, read BOOLEAN DEFAULT FALSE,
               created_at TIMESTAMP)
```

---

## 13. API: все эндпоинты

### Auth Service (/api/auth)

| Метод | Путь | Тело | Доступ | Описание |
|-------|------|------|--------|----------|
| POST | /register | {username, email, password} | Все | Регистрация |
| POST | /login | {username, password} | Все | Вход, получить токены |
| POST | /refresh | {refreshToken} | Все | Обновить пару токенов |
| POST | /logout | {refreshToken} | Авторизованные | Выход |

### Movie Service (/api/movies, /api/genres)

| Метод | Путь | Параметры | Доступ |
|-------|------|-----------|--------|
| GET | /api/movies | ?genre=&type=&durationMax=&page=&size= | Все |
| GET | /api/movies/{id} | — | Все |
| POST | /api/movies | тело: MovieCreateRequest | ADMIN |
| PUT | /api/movies/{id} | тело: MovieCreateRequest | ADMIN |
| DELETE | /api/movies/{id} | — | ADMIN |
| POST | /api/movies/{id}/reviews | {rating:1-5, comment} | CLIENT |
| POST | /api/movies/{id}/comments | {text} | CLIENT |
| GET | /api/genres | — | Все |
| POST | /api/genres | {name} | ADMIN |

### Hall Service (/api/halls, /api/sessions)

| Метод | Путь | Доступ |
|-------|------|--------|
| GET | /api/halls | Все |
| POST | /api/halls | ADMIN |
| PUT | /api/halls/{id} | ADMIN |
| DELETE | /api/halls/{id} | ADMIN |
| GET | /api/halls/{id}/extra-services | Все |
| POST | /api/halls/{id}/extra-services | ADMIN |
| GET | /api/sessions?movieId=&hallId=&from=&to= | Все |
| GET | /api/sessions/{id} | Все |
| POST | /api/sessions | ADMIN |
| PUT | /api/sessions/{id} | ADMIN |
| DELETE | /api/sessions/{id} | ADMIN |

### Order Service (/api/orders, /api/food-menu)

| Метод | Путь | Доступ |
|-------|------|--------|
| POST | /api/orders/ticket | CLIENT |
| POST | /api/orders/ticket/by-seller | SELLER |
| POST | /api/orders/food | SELLER |
| GET | /api/orders/my | CLIENT |
| GET | /api/orders/{id} | CLIENT/SELLER |
| POST | /api/orders/webhook/payment | Публичный (платёжный шлюз) |
| GET | /api/food-menu | Все |
| POST | /api/food-menu | ADMIN |

### Support Service (/api/support)

| Метод | Путь | Доступ |
|-------|------|--------|
| POST | /api/support/tickets | CLIENT |
| GET | /api/support/tickets/my | CLIENT |
| GET | /api/support/tickets | ADMIN |
| POST | /api/support/tickets/{id}/messages | CLIENT, ADMIN |
| GET | /api/support/tickets/{id}/messages | CLIENT, ADMIN |
| PUT | /api/support/tickets/{id}/assign | ADMIN |
| PATCH | /api/support/tickets/{id}/close | ADMIN |

### Notification Service (/api/notifications)

| Метод | Путь | Доступ |
|-------|------|--------|
| GET | /api/notifications | Авторизованные |
| PATCH | /api/notifications/{id}/read | Авторизованные |

---

## 14. Структура проекта

```
Pet_Cinema/
│
├── settings.gradle.kts     ← Kotlin DSL: список всех модулей
├── build.gradle.kts        ← Общий build: Spring Boot BOM, Lombok, Actuator для всех
├── docker-compose.yml      ← Вся инфраструктура одним файлом
├── CLAUDE.md               ← Техническая документация для разработки
│
├── common-dtos/            ← Общие DTO (нет Spring Boot, только Java + Lombok)
│   └── src/main/java/com/cinema/dto/
│       ├── auth/           ← AuthRequest, AuthResponse, ...
│       ├── movie/          ← MovieDto, ReviewDto, ...
│       ├── hall/           ← HallDto, SessionDto, ...
│       ├── order/          ← OrderDto, TicketDto, ...
│       ├── support/        ← SupportTicketDto, ...
│       ├── notification/   ← NotificationDto
│       ├── event/          ← Kafka события (TicketPurchaseEvent, ...)
│       └── common/         ← ErrorResponse, PageResponse<T>
│
├── service-discovery/      ← Eureka Server
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/cinema/discovery/ServiceDiscoveryApplication.java
│       └── resources/application.yml
│
├── api-gateway/            ← Spring Cloud Gateway
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/main/java/com/cinema/gateway/
│       ├── ApiGatewayApplication.java
│       ├── filter/
│       │   ├── JwtAuthenticationFilter.java   ← JWT проверка
│       │   └── CacheInvalidationConsumer.java ← Kafka → Redis
│       └── util/JwtUtils.java
│
├── auth-service/
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/main/java/com/cinema/auth/
│       ├── AuthServiceApplication.java
│       ├── entity/         ← User, Role, RefreshToken
│       ├── repository/     ← UserRepository, RefreshTokenRepository
│       ├── security/       ← JwtUtils, UserDetailsServiceImpl
│       ├── filter/         ← JwtAuthFilter
│       ├── service/        ← AuthService (register/login/refresh/logout)
│       ├── controller/     ← AuthController
│       ├── config/         ← SecurityConfig, RedisConfig, DataLoader
│       └── exception/      ← GlobalExceptionHandler
│
├── movie-service/          ← аналогичная структура
├── hall-service/           ← аналогичная структура
├── order-service/          ← аналогичная структура
├── support-service/        ← аналогичная структура
├── notification-service/   ← аналогичная структура
├── payment-simulator/      ← аналогичная структура
│
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   ├── Dockerfile          ← multi-stage: node builder + nginx
│   ├── nginx.conf
│   └── src/
│       ├── main.tsx
│       ├── App.tsx          ← BrowserRouter + Routes
│       ├── index.css
│       ├── types/index.ts   ← TypeScript интерфейсы
│       ├── api/axios.ts     ← Axios + interceptors
│       ├── context/AuthContext.tsx
│       ├── components/Layout.tsx
│       └── pages/
│           ├── HomePage.tsx
│           ├── MovieDetailPage.tsx
│           ├── SessionsPage.tsx
│           ├── BookingPage.tsx
│           ├── LoginPage.tsx
│           ├── RegisterPage.tsx
│           ├── ProfilePage.tsx
│           ├── SupportPage.tsx
│           ├── AdminPage.tsx
│           └── SellerPage.tsx
│
└── infrastructure/
    ├── loki/loki-config.yml
    ├── promtail/promtail-config.yml
    └── grafana/provisioning/
        ├── datasources/loki.yml
        └── dashboards/
            ├── dashboard.yml
            └── cinema-logs.json
```

---

## Краткое резюме ключевых концепций

| Концепция | Где применена | Зачем |
|-----------|--------------|-------|
| **Микросервисы** | Весь проект | Независимое развёртывание, масштабирование |
| **API Gateway** | api-gateway | Единая точка входа, безопасность |
| **Service Discovery** | Eureka | Динамические адреса в Docker |
| **JWT** | Все сервисы | Stateless аутентификация |
| **Kafka** | 4 топика | Асинхронное общение, надёжность |
| **Redis** | movie-service, auth-service | Быстрый кеш, blacklist токенов |
| **Docker Compose** | Весь стек | Воспроизводимое окружение |
| **Spring Security** | Все сервисы | Авторизация по ролям |
| **React SPA** | Frontend | Клиентский роутинг, без перезагрузки |
