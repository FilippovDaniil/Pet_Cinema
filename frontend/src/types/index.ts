// TypeScript интерфейсы — описывают форму данных, получаемых от API.
// Соответствуют Java DTO классам в common-dtos модуле.
// Используются для type safety: TypeScript проверяет корректность использования полей.

// User — информация об аутентифицированном пользователе.
// Соответствует: com.cinema.dto.auth.UserDto.
// Декодируется из JWT payload в AuthContext.
export interface User {
  id: number;                                        // userId из JWT sub claim
  username: string;                                  // из UserDto (не из JWT — пустая строка в AuthContext)
  email: string;                                     // из UserDto (не из JWT — пустая строка)
  role: 'ROLE_CLIENT' | 'ROLE_SELLER' | 'ROLE_ADMIN'; // из JWT roles claim
}

// AuthResponse — ответ при login/register/refresh.
// Соответствует: com.cinema.dto.auth.AuthResponse.
export interface AuthResponse {
  accessToken: string;   // JWT access token (TTL 15 мин)
  refreshToken: string;  // JWT refresh token (TTL 7 дней, хранится в БД)
}

// Movie — данные фильма.
// Соответствует: com.cinema.dto.movie.MovieDto.
export interface Movie {
  id: number;
  title: string;
  description: string;
  posterUrl: string;               // URL постера (внешняя ссылка или путь)
  durationMinutes: number;         // продолжительность в минутах
  type: 'TWO_D' | 'THREE_D' | 'FIVE_D'; // формат фильма (MovieType enum в Java)
  genres: string[];                // список названий жанров (не объектов)
  averageRating: number;           // средний рейтинг из отзывов (вычисляется в MovieService)
}

// Genre — жанр фильма.
// Соответствует: com.cinema.dto.movie.GenreDto.
export interface Genre {
  id: number;
  name: string; // уникальное имя (unique constraint в БД)
}

// Hall — кинозал.
// Соответствует: com.cinema.dto.hall.HallDto.
export interface Hall {
  id: number;
  name: string;                                         // например "Зал VIP"
  type: 'NORMAL' | 'VIP' | 'THREE_D' | 'FIVE_D';       // HallType enum
  rowsCount: number;                                    // количество рядов
  seatsPerRow: number;                                  // мест в ряду
  description: string;
}

// ExtraService — дополнительная услуга в зале (вибрация кресла, 3D очки и т.д.).
// Соответствует: com.cinema.dto.hall.ExtraServiceDto.
export interface ExtraService {
  id: number;
  hallId: number;   // ID зала которому принадлежит услуга
  name: string;     // название услуги
  price: number;    // цена в рублях
}

// Session — киносеанс.
// Соответствует: com.cinema.dto.hall.SessionDto.
export interface Session {
  id: number;
  movieId: number;       // ID фильма (Long в Java, number в TypeScript)
  hallId: number;        // ID зала
  startTime: string;     // ISO datetime строка: "2026-05-14T12:00:00"
  endTime: string;       // ISO datetime строка
  basePrice: number;     // базовая цена билета в рублях
  active: boolean;       // сеанс активен (можно купить билет)
}

// Review — отзыв на фильм (с рейтингом).
// Соответствует: com.cinema.dto.movie.ReviewDto.
export interface Review {
  id: number;
  movieId: number;
  userId: number;
  rating: number;       // 1-5 звёзд
  comment: string;      // текст отзыва
  createdAt: string;    // ISO datetime
}

// Comment — комментарий к фильму (без рейтинга).
// Соответствует: com.cinema.dto.movie.CommentDto.
export interface Comment {
  id: number;
  movieId: number;
  userId: number;
  text: string;          // текст комментария
  createdAt: string;
}

// Order — заказ (билеты, еда или смешанный).
// Соответствует: com.cinema.dto.order.OrderDto.
export interface Order {
  id: number;
  userId: number;
  sellerId: number;                        // если продавец оформлял (может быть null)
  orderType: 'TICKET' | 'FOOD' | 'MIXED'; // тип заказа
  status: 'PENDING' | 'PAID' | 'CANCELLED'; // статус (PENDING = ожидает оплаты)
  totalPrice: number;
  items: OrderItem[];                      // позиции заказа
  createdAt: string;
}

// OrderItem — позиция в заказе (один билет или одна позиция еды).
// Соответствует: com.cinema.dto.order.OrderItemDto.
export interface OrderItem {
  id: number;
  orderId: number;
  itemType: 'TICKET' | 'FOOD'; // тип позиции
  ticketSessionId: number;     // ID сеанса (для билета)
  ticketSeatRow: number;       // ряд (для билета)
  ticketSeatNumber: number;    // место (для билета)
  foodItemId: number;          // ID блюда (для еды)
  quantity: number;            // количество (для еды)
  price: number;               // цена позиции
}

// FoodItem — блюдо из меню.
// Соответствует: com.cinema.dto.order.FoodItemDto.
export interface FoodItem {
  id: number;
  name: string;
  price: number;
  category: string; // FoodCategory enum: DRINK, POPCORN, SNACK, OTHER
}

// SupportTicket — тикет в техподдержку.
// Соответствует: com.cinema.dto.support.SupportTicketDto.
export interface SupportTicket {
  id: number;
  clientId: number;          // ID клиента создавшего тикет
  adminId: number;           // ID назначенного администратора (0/null если не назначен)
  subject: string;           // тема тикета
  status: 'OPEN' | 'CLOSED'; // статус (TicketStatus enum в Java)
  createdAt: string;
}

// SupportMessage — сообщение в тикете поддержки.
// Соответствует: com.cinema.dto.support.SupportMessageDto.
export interface SupportMessage {
  id: number;
  ticketId: number;  // ID тикета
  senderId: number;  // ID отправителя (клиент или администратор)
  content: string;   // текст сообщения
  sentAt: string;    // ISO datetime
}

// Notification — уведомление пользователю.
// Соответствует: com.cinema.dto.notification.NotificationDto.
export interface Notification {
  id: number;
  userId: number;
  title: string;     // заголовок (например "Билет куплен!")
  content: string;   // текст уведомления
  read: boolean;     // прочитано (false = новое, не прочитанное)
  createdAt: string;
}

// PageResponse<T> — страничный ответ (пагинация).
// Соответствует: com.cinema.dto.common.PageResponse<T>.
// Дженерик T — тип элементов (например PageResponse<Movie>).
export interface PageResponse<T> {
  content: T[];          // элементы текущей страницы
  totalElements: number; // всего элементов в БД
  totalPages: number;    // всего страниц
  page: number;          // текущая страница (0-based)
  size: number;          // размер страницы
}
