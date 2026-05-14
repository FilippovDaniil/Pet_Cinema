package com.cinema.auth.entity; // Пакет JPA-сущностей auth-service

public enum Role {
    // Перечисление ролей пользователей системы.
    // Хранится в БД как строка (ROLE_CLIENT / ROLE_SELLER / ROLE_ADMIN) благодаря @Enumerated(EnumType.STRING)
    // Также помещается в JWT в поле "roles": ["ROLE_CLIENT"]

    ROLE_CLIENT,  // Обычный клиент: может покупать билеты, оставлять отзывы, открывать тикеты поддержки
    ROLE_SELLER,  // Кассир/продавец: оформляет билеты и еду от имени клиента, имеет доступ к SellerPage
    ROLE_ADMIN    // Администратор: управляет фильмами, залами, сеансами, пользователями
}
