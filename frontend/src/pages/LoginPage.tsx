// useState — хук для локального стейта формы (username, password, loading, error).
import { useState } from 'react';

// useNavigate — программная навигация (navigate('/') после успешного входа).
// Link — ссылка на страницу регистрации без перезагрузки.
import { useNavigate, Link } from 'react-router-dom';

// api — настроенный axios (базовый URL /api + JWT interceptor).
import api from '../api/axios';

// useAuth — хук для доступа к функции login() из AuthContext.
import { useAuth } from '../context/AuthContext';

// AuthResponse — TypeScript тип ответа { accessToken, refreshToken }.
import { AuthResponse } from '../types';

// LoginPage — страница входа в систему.
export default function LoginPage() {
  const navigate = useNavigate();
  // login — функция из AuthContext: сохраняет токены и устанавливает user в стейт.
  const { login } = useAuth();

  // Стейт формы
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false); // блокируем кнопку во время запроса
  const [error, setError] = useState('');         // ошибка аутентификации

  // handleSubmit — обработчик отправки формы.
  // React.FormEvent — TypeScript тип события формы.
  const handleSubmit = async (e: React.FormEvent) => {
    // e.preventDefault() — предотвращаем стандартное поведение формы (перезагрузку страницы).
    // Без этого браузер перезагрузит страницу при submit.
    e.preventDefault();

    // Простая клиентская валидация перед отправкой запроса.
    // .trim() — убираем пробелы (пустые пробелы не считаем заполненными).
    if (!username.trim() || !password.trim()) {
      setError('Заполните все поля');
      return;
    }

    setLoading(true);
    setError('');
    try {
      // POST /api/auth/login с { username, password }.
      // Без Authorization заголовка — это публичный endpoint (в whitelist api-gateway).
      // <AuthResponse> — TypeScript generic: res.data будет типа AuthResponse.
      const res = await api.post<AuthResponse>('/auth/login', { username, password });

      // login() — сохраняем токены в localStorage и устанавливаем user в AuthContext.
      // После этого isAuthenticated = true, компоненты которые зависят от этого перерендерятся.
      login(res.data.accessToken, res.data.refreshToken);

      // navigate('/') — переходим на главную страницу.
      navigate('/');
    } catch (e: any) {
      // e.response?.data?.message — сообщение об ошибке от сервера (из GlobalExceptionHandler).
      // Пример: "Неверные учётные данные" при AuthException в auth-service.
      // || — fallback если нет сообщения от сервера.
      setError(e.response?.data?.message || 'Неверное имя пользователя или пароль');
    } finally {
      setLoading(false);
    }
  };

  // Стиль карточки формы (центрированная в странице).
  const formContainerStyle: React.CSSProperties = {
    maxWidth: '420px',
    margin: '3rem auto',       // вертикальный отступ 3rem, горизонтальный auto = центрирование
    background: '#1a1a1a',
    borderRadius: '12px',
    padding: '2.5rem',
    border: '1px solid #2a2a2a',
    boxShadow: '0 8px 32px rgba(0,0,0,0.5)', // тень для "поднятости"
  };

  // Стиль полей ввода.
  const inputStyle: React.CSSProperties = {
    width: '100%',
    background: '#111',
    border: '1.5px solid #333',
    borderRadius: '8px',
    color: '#fff',
    padding: '0.8rem 1rem',
    fontSize: '0.95rem',
    outline: 'none',           // убираем дефолтный outline браузера
    transition: 'border-color 0.2s', // плавная смена цвета рамки при focus
    marginBottom: '1rem',
  };

  return (
    <div style={formContainerStyle}>
      {/* Заголовок формы */}
      <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
        <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>🎬</div>
        <h1 style={{ fontSize: '1.6rem', fontWeight: 'bold', marginBottom: '0.3rem' }}>Вход в систему</h1>
        <p style={{ color: '#666', fontSize: '0.9rem' }}>Добро пожаловать в CinemaSystem</p>
      </div>

      {/* onSubmit — привязываем обработчик к событию submit формы */}
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '0.2rem' }}>
          <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
            Имя пользователя
          </label>
          <input
            type="text"
            value={username}  // controlled input: значение из стейта
            // onChange — обновляем стейт при каждом нажатии клавиши.
            // e.target.value — текущее значение поля.
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Введите имя пользователя"
            autoComplete="username" // подсказка браузеру для автозаполнения
            style={inputStyle}
            // Hover эффект через обработчики событий (React.CSSProperties не поддерживает :focus).
            onFocus={(e) => (e.target.style.borderColor = '#e50914')}  // красная рамка при фокусе
            onBlur={(e) => (e.target.style.borderColor = '#333')}      // серая рамка при потере фокуса
          />
        </div>

        <div style={{ marginBottom: '0.5rem' }}>
          <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
            Пароль
          </label>
          <input
            type="password"   // type="password" — скрывает символы (точки/звёздочки)
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Введите пароль"
            autoComplete="current-password"
            style={inputStyle}
            onFocus={(e) => (e.target.style.borderColor = '#e50914')}
            onBlur={(e) => (e.target.style.borderColor = '#333')}
          />
        </div>

        {/* Блок ошибки — показывается только при error !== '' */}
        {error && (
          <div style={{
            background: '#2a0a0a',         // тёмно-красный фон
            border: '1px solid #e50914',
            borderRadius: '6px',
            padding: '0.7rem 1rem',
            marginBottom: '1rem',
            color: '#ff6b6b',             // светло-красный текст
            fontSize: '0.9rem',
          }}>
            {error}
          </div>
        )}

        <button
          type="submit"           // type="submit" — кнопка отправляет форму (вызывает onSubmit)
          disabled={loading}      // заблокирован во время загрузки (предотвращает двойной submit)
          style={{
            width: '100%',
            background: loading ? '#555' : '#e50914', // серый при загрузке, красный иначе
            color: '#fff',
            border: 'none',
            borderRadius: '8px',
            padding: '0.9rem',
            fontSize: '1rem',
            fontWeight: '700',
            cursor: loading ? 'not-allowed' : 'pointer', // курсор-запрет при загрузке
            marginBottom: '1.2rem',
            transition: 'background 0.2s',
          }}
        >
          {/* Текст кнопки меняется в зависимости от состояния загрузки */}
          {loading ? 'Вход...' : 'Войти'}
        </button>
      </form>

      {/* Ссылка на регистрацию */}
      <div style={{ textAlign: 'center', color: '#666', fontSize: '0.9rem' }}>
        Нет аккаунта?{' '}
        {/* {' '} — пробел как JSX выражение (нельзя просто написать пробел между текстом и Link) */}
        <Link to="/register" style={{ color: '#e50914', fontWeight: '600' }}>
          Зарегистрироваться
        </Link>
      </div>
    </div>
  );
}
