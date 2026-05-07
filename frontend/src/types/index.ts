export interface User {
  id: number;
  username: string;
  email: string;
  role: 'ROLE_CLIENT' | 'ROLE_SELLER' | 'ROLE_ADMIN';
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface Movie {
  id: number;
  title: string;
  description: string;
  posterUrl: string;
  durationMinutes: number;
  type: 'TWO_D' | 'THREE_D' | 'FIVE_D';
  genres: string[];
  averageRating: number;
}

export interface Genre {
  id: number;
  name: string;
}

export interface Hall {
  id: number;
  name: string;
  type: 'NORMAL' | 'VIP' | 'THREE_D' | 'FIVE_D';
  rowsCount: number;
  seatsPerRow: number;
  description: string;
}

export interface ExtraService {
  id: number;
  hallId: number;
  name: string;
  price: number;
}

export interface Session {
  id: number;
  movieId: number;
  hallId: number;
  startTime: string;
  endTime: string;
  basePrice: number;
  active: boolean;
}

export interface Review {
  id: number;
  movieId: number;
  userId: number;
  rating: number;
  comment: string;
  createdAt: string;
}

export interface Comment {
  id: number;
  movieId: number;
  userId: number;
  text: string;
  createdAt: string;
}

export interface Order {
  id: number;
  userId: number;
  sellerId: number;
  orderType: 'TICKET' | 'FOOD' | 'MIXED';
  status: 'PENDING' | 'PAID' | 'CANCELLED';
  totalPrice: number;
  items: OrderItem[];
  createdAt: string;
}

export interface OrderItem {
  id: number;
  orderId: number;
  itemType: 'TICKET' | 'FOOD';
  ticketSessionId: number;
  ticketSeatRow: number;
  ticketSeatNumber: number;
  foodItemId: number;
  quantity: number;
  price: number;
}

export interface FoodItem {
  id: number;
  name: string;
  price: number;
  category: string;
}

export interface SupportTicket {
  id: number;
  clientId: number;
  adminId: number;
  subject: string;
  status: 'OPEN' | 'CLOSED';
  createdAt: string;
}

export interface SupportMessage {
  id: number;
  ticketId: number;
  senderId: number;
  content: string;
  sentAt: string;
}

export interface Notification {
  id: number;
  userId: number;
  title: string;
  content: string;
  read: boolean;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}
