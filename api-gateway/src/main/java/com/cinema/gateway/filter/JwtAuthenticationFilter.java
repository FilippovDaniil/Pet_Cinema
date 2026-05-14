package com.cinema.gateway.filter; // Пакет фильтров API Gateway

import com.cinema.gateway.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;                        // Интерфейс фильтра для Gateway
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory; // Базовый класс для фабрик фильтров
import org.springframework.core.Ordered;                                               // Интерфейс для задания приоритета фильтра
import org.springframework.http.HttpHeaders;                                           // Константы HTTP-заголовков (AUTHORIZATION и т.д.)
import org.springframework.http.HttpMethod;                                            // Enum методов HTTP (GET, POST, PUT, DELETE)
import org.springframework.http.HttpStatus;                                            // HTTP статус-коды (401, 403 и т.д.)
import org.springframework.http.server.reactive.ServerHttpRequest;                    // Реактивный HTTP-запрос (Netty, не Servlet!)
import org.springframework.http.server.reactive.ServerHttpResponse;                   // Реактивный HTTP-ответ
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;                               // Обёртка над парой запрос+ответ
import reactor.core.publisher.Mono;                                                    // Реактивный тип — 0 или 1 элемент

import java.util.List;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config>
        implements Ordered {
    // Фильтр проверяет JWT перед проксированием запроса к нужному микросервису.
    // Является GatewayFilterFactory — применяется через yaml: filters: - name: JwtAuthenticationFilter

    // Пути, открытые для ЛЮБОГО метода (без токена):
    private static final List<String> PUBLIC_ANY_METHOD = List.of(
            "/api/auth/register",        // Регистрация нового пользователя
            "/api/auth/login",           // Вход (получение токенов)
            "/api/auth/refresh",         // Обновление пары токенов
            "/api/orders/webhook/"       // Вебхук от payment-simulator (внутренний вызов)
    );

    // Пути, открытые только для GET (чтение без токена, запись — требует авторизации):
    private static final List<String> PUBLIC_GET_PATHS = List.of(
            "/api/movies",    // Список фильмов и детали — публично
            "/api/genres",    // Список жанров — публично
            "/api/sessions",  // Список сеансов — публично
            "/api/halls",     // Список залов — публично
            "/api/food-menu"  // Меню еды — публично
    );

    private final JwtUtils jwtUtils; // Утилита для валидации JWT

    @Autowired
    public JwtAuthenticationFilter(JwtUtils jwtUtils) {
        super(Config.class); // Передаём класс конфигурации фильтра в базовый класс
        this.jwtUtils = jwtUtils;
    }

    @Override
    public GatewayFilter apply(Config config) {
        // Возвращает GatewayFilter — лямбда, вызываемая при каждом запросе
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest(); // Получаем входящий запрос

            if (isPublicRequest(request)) {
                return chain.filter(exchange); // Публичный путь — пропускаем без проверки токена
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION); // Читаем заголовок "Authorization"

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onUnauthorized(exchange); // Нет заголовка или неверный формат → 401
            }

            String token = authHeader.substring(7); // Убираем "Bearer " (7 символов) → получаем чистый JWT

            if (!jwtUtils.validateToken(token)) {
                return onUnauthorized(exchange); // Токен невалиден или истёк → 401
            }

            return chain.filter(exchange); // Токен валиден → проксируем запрос дальше
        };
    }

    private boolean isPublicRequest(ServerHttpRequest request) {
        String path = request.getURI().getPath(); // Путь запроса, например "/api/movies/5"

        if (PUBLIC_ANY_METHOD.stream().anyMatch(path::startsWith)) {
            return true; // Путь начинается с одного из публичных — пропускаем
        }

        // GET-запросы к публичным путям тоже открыты
        return HttpMethod.GET.equals(request.getMethod())
                && PUBLIC_GET_PATHS.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onUnauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED); // Устанавливаем статус 401
        return response.setComplete(); // Завершаем ответ (Mono<Void> — реактивный способ)
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
        // Выполняется первым среди фильтров (приоритет = MIN_VALUE + 1)
        // Важно: JWT проверяется ДО любой другой логики маршрутизации
    }

    public static class Config {
        // Пустой класс конфигурации — требуется AbstractGatewayFilterFactory
        // Если нужны параметры фильтра в yaml (например, excluded paths), они добавляются сюда
    }
}
