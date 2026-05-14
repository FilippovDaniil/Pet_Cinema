// axios — популярная HTTP библиотека для браузера и Node.js.
// Альтернатива: fetch API (встроен в браузер, нет интерцепторов из коробки).
// axios преимущества: интерцепторы запросов/ответов, автоматический JSON parse, отмена запросов.
import axios from 'axios';

// axios.create() — создаёт изолированный экземпляр axios со своими настройками.
// Отличие от глобального axios: у нас своя конфигурация, свои интерцепторы.
// Экспортируем `api` — используется во всех страницах вместо глобального axios.
const api = axios.create({
  // baseURL: '/api' — базовый URL для всех запросов.
  // '/api' (относительный путь) — запросы идут на тот же домен откуда загружена страница.
  //   В dev режиме (Vite): vite.config.ts проксирует /api → http://localhost:8080/api
  //   В Docker (Nginx): nginx.conf проксирует /api → http://api-gateway:8080/api
  // Пример: api.get('/movies') → GET /api/movies → api-gateway → movie-service
  baseURL: '/api',
});

// api.interceptors.request.use() — добавляем интерцептор для КАЖДОГО исходящего запроса.
// Интерцептор запроса выполняется ДО отправки запроса.
// Здесь: добавляем JWT токен в Authorization заголовок.
api.interceptors.request.use((config) => {
  // localStorage.getItem('accessToken') — читаем токен из локального хранилища браузера.
  // localStorage — персистентное хранилище (не теряется при закрытии вкладки).
  // Токен сохраняется в AuthContext.login() при успешной авторизации.
  const token = localStorage.getItem('accessToken');

  if (token) {
    // config.headers.Authorization — устанавливаем заголовок Authorization.
    // `Bearer ${token}` — стандартный формат Bearer токена (RFC 6750).
    // api-gateway JwtAuthenticationFilter извлекает токен из этого заголовка.
    config.headers.Authorization = `Bearer ${token}`;
  }

  // Возвращаем изменённый config — запрос продолжается с добавленным заголовком.
  return config;
});

// api.interceptors.response.use() — интерцептор для КАЖДОГО входящего ответа.
// Первый параметр: обработчик успешного ответа (2xx статусы).
// Второй параметр: обработчик ошибки (не 2xx статусы).
api.interceptors.response.use(
  // Успешный ответ — пропускаем без изменений.
  (response) => response,

  // Обработчик ошибки — async т.к. делаем повторный запрос при 401.
  async (error) => {
    // error.response?.status === 401 — Access Token истёк или невалиден.
    // ?. (optional chaining) — если response undefined (сетевая ошибка) → условие false.
    if (error.response?.status === 401) {
      // Пытаемся обновить токены через refresh token.
      const refreshToken = localStorage.getItem('refreshToken');

      if (refreshToken) {
        try {
          // POST /api/auth/refresh — запрос нового access token.
          // Используем глобальный axios (не наш api) чтобы избежать рекурсии интерцепторов.
          // Тело запроса: { refreshToken } — отправляем refresh token для ротации.
          const response = await axios.post('/api/auth/refresh', { refreshToken });

          // Деструктурируем ответ: новые токены.
          const { accessToken, refreshToken: newRefresh } = response.data;

          // Сохраняем новые токены в localStorage.
          localStorage.setItem('accessToken', accessToken);
          localStorage.setItem('refreshToken', newRefresh);

          // Обновляем заголовок в оригинальном запросе (error.config = конфиг упавшего запроса).
          error.config.headers.Authorization = `Bearer ${accessToken}`;

          // Повторяем оригинальный запрос с новым токеном.
          // api(error.config) — повторный вызов через наш экземпляр axios с той же конфигурацией.
          return api(error.config);
        } catch {
          // Refresh токен тоже невалиден (истёк 7 дней, или logout другой вкладкой).
          // localStorage.clear() — очищаем все данные авторизации.
          localStorage.clear();
          // Перенаправляем на /login для повторной авторизации.
          window.location.href = '/login';
        }
      }
    }

    // Для всех остальных ошибок (400, 403, 404, 500) — пробрасываем ошибку дальше.
    // Компоненты страниц обрабатывают ошибки через try/catch или .catch().
    return Promise.reject(error);
  }
);

// Экспортируем настроенный экземпляр axios как default export.
// Использование в компонентах:
//   import api from '../api/axios';
//   const movies = await api.get<Movie[]>('/movies');
export default api;
