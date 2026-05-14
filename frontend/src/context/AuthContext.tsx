import React, { createContext, useContext, useState, useEffect } from 'react';
// createContext — создаёт React Context объект для передачи данных без пропсов через дерево.
// useContext — хук для чтения значения из контекста в любом дочернем компоненте.
// useState — хук для локального стейта (user, accessToken).
// useEffect — хук для побочных эффектов (восстановление сессии при загрузке).

import { User } from '../types';
// User — TypeScript интерфейс { id, username, email, role }.

// JwtPayload — структура декодированного payload JWT токена.
// Соответствует claims которые JwtUtils.generateAccessToken() записывает в Java:
//   Claims.subject(userId) → sub
//   claim("roles", roles) → roles
//   Claims.expiration(date) → exp (Unix timestamp в секундах)
interface JwtPayload {
  sub: string;       // subject = userId (как строка: "42")
  roles: string[];   // роли: ["ROLE_CLIENT"] или ["ROLE_ADMIN"]
  exp: number;       // Unix timestamp истечения (секунды, не миллисекунды!)
}

// decodeToken — декодирует JWT payload без верификации подписи.
// JWT структура: header.payload.signature (три части, разделённые точкой).
// payload закодирован в Base64Url (не Base64 — заменены +→-, /→_).
// ВАЖНО: это декодирование, НЕ верификация. Подпись не проверяется на фронтенде —
//   это нормально: верификацию выполняет api-gateway (JwtUtils.validateToken).
function decodeToken(token: string): JwtPayload | null {
  try {
    // token.split('.')[1] — берём вторую часть (payload между двумя точками).
    const base64 = token.split('.')[1];

    // atob(base64) — декодирует Base64 строку в JSON строку.
    // atob — встроенная браузерная функция (нет аналога в Node.js без Buffer).
    // JSON.parse — парсим JSON строку в объект JwtPayload.
    const decoded = JSON.parse(atob(base64));
    return decoded;
  } catch {
    // Ошибка при декодировании (невалидный токен, обрезанный строку и т.д.) → null.
    return null;
  }
}

// AuthContextType — TypeScript интерфейс для значения контекста.
// Описывает все данные и методы, доступные через useAuth().
interface AuthContextType {
  user: User | null;              // данные пользователя (null если не авторизован)
  accessToken: string | null;     // текущий access token
  login: (accessToken: string, refreshToken: string) => void; // вызывается после успешного login/register
  logout: () => void;             // выход из системы
  isAuthenticated: boolean;       // true если user !== null
  isAdmin: boolean;               // user.role === 'ROLE_ADMIN'
  isSeller: boolean;              // user.role === 'ROLE_SELLER'
  isClient: boolean;              // user.role === 'ROLE_CLIENT'
}

// createContext<AuthContextType>({} as AuthContextType) — создаём Context с дефолтным значением.
// {} as AuthContextType — TypeScript type assertion: говорим "доверяй нам, это AuthContextType".
// На практике дефолтное значение никогда не используется — AuthProvider всегда оборачивает дерево.
const AuthContext = createContext<AuthContextType>({} as AuthContextType);

// AuthProvider — компонент-провайдер.
// Оборачивает всё приложение в App.tsx: <AuthProvider>...</AuthProvider>.
// Хранит глобальный стейт авторизации и предоставляет его дочерним компонентам.
export function AuthProvider({ children }: { children: React.ReactNode }) {
  // user — данные авторизованного пользователя (null если не авторизован).
  const [user, setUser] = useState<User | null>(null);

  // accessToken — текущий access token (null если не авторизован).
  const [accessToken, setAccessToken] = useState<string | null>(null);

  // useEffect(() => {...}, []) — эффект с пустым массивом зависимостей.
  // Выполняется ОДИН РАЗ при монтировании компонента (при загрузке страницы).
  // Цель: восстановить сессию из localStorage если пользователь уже был авторизован.
  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      const payload = decodeToken(token);
      if (payload && payload.exp * 1000 > Date.now()) {
        // payload.exp * 1000 — конвертируем секунды в миллисекунды (Date.now() в мс).
        // > Date.now() — токен ещё не истёк → восстанавливаем сессию.
        setAccessToken(token);
        setUser({
          id: parseInt(payload.sub), // sub — строка "42" → число 42
          username: '',              // не в JWT payload (только id и role)
          email: '',
          role: payload.roles[0] as User['role'], // берём первую роль из массива
        });
      } else {
        // Токен истёк → удаляем из localStorage (нет смысла хранить просроченный токен).
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        // user остаётся null → пользователь увидит страницу без авторизации.
      }
    }
  }, []); // [] — пустой массив: эффект только при монтировании, не при каждом рендере

  // login — вызывается из LoginPage и RegisterPage при успешной авторизации.
  // Сохраняет токены и устанавливает стейт пользователя.
  const login = (token: string, refresh: string) => {
    // localStorage — токены сохраняются между сессиями браузера (закрытие/открытие вкладки).
    localStorage.setItem('accessToken', token);
    localStorage.setItem('refreshToken', refresh);
    setAccessToken(token);

    const payload = decodeToken(token);
    if (payload) {
      setUser({
        id: parseInt(payload.sub),
        username: '',
        email: '',
        role: payload.roles[0] as User['role'],
      });
    }
  };

  // logout — выход из системы.
  // 1. Отправляет refresh token на /auth/logout (добавляет в Redis blacklist)
  // 2. Очищает localStorage
  // 3. Сбрасывает стейт (user = null, accessToken = null)
  const logout = () => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
      // import('../api/axios') — динамический импорт (lazy load).
      // Избегает circular dependency: AuthContext ← api/axios ← AuthContext.
      // .then(({ default: api }) — деструктурируем default export модуля.
      import('../api/axios').then(({ default: api }) => {
        // api.post('/auth/logout') — fire and forget (не ждём ответа).
        // .catch(() => {}) — игнорируем ошибки (logout на фронтенде всегда успешен).
        api.post('/auth/logout', { refreshToken }).catch(() => {});
      });
    }

    // localStorage.clear() — удаляем ВСЕ данные из localStorage (токены).
    localStorage.clear();

    // Сбрасываем React стейт → все компоненты зависящие от user получат null.
    setUser(null);
    setAccessToken(null);
  };

  return (
    // AuthContext.Provider — предоставляет значение контекста всем дочерним компонентам.
    // value — объект со всеми данными и методами авторизации.
    <AuthContext.Provider value={{
      user,
      accessToken,
      login,
      logout,
      isAuthenticated: !!user,              // !! — double negation: преобразует object|null в boolean
      isAdmin: user?.role === 'ROLE_ADMIN',  // ?. — optional chaining: user?.role = undefined если user null
      isSeller: user?.role === 'ROLE_SELLER',
      isClient: user?.role === 'ROLE_CLIENT',
    }}>
      {/* children — всё приложение (BrowserRouter + Routes + страницы) */}
      {children}
    </AuthContext.Provider>
  );
}

// useAuth — кастомный хук для удобного доступа к AuthContext.
// Использование в компонентах:
//   const { user, isAuthenticated, logout } = useAuth();
// Без useAuth пришлось бы писать:
//   const auth = useContext(AuthContext);
export const useAuth = () => useContext(AuthContext);
