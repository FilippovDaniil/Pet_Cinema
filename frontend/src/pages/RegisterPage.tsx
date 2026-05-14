import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axios';

// RegisterPage — страница регистрации нового пользователя.
// Доступна без авторизации (публичный маршрут в App.tsx).
export default function RegisterPage() {
  const navigate = useNavigate();

  // Стейт всех полей формы регистрации.
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  // confirmPassword — поле подтверждения пароля (только на фронтенде, не отправляется на бэкенд).
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  // success — флаг успешной регистрации (показываем сообщение и редиректим).
  const [success, setSuccess] = useState(false);

  // handleSubmit — обработчик отправки формы регистрации.
  const handleSubmit = async (e: React.FormEvent) => {
    // e.preventDefault() — предотвращаем стандартное поведение: перезагрузку страницы.
    e.preventDefault();

    // Клиентская валидация перед отправкой на сервер.
    if (!username.trim() || !email.trim() || !password.trim()) {
      setError('Заполните все поля');
      return;
    }
    // Пароли должны совпадать (проверяется ТОЛЬКО на фронтенде).
    if (password !== confirmPassword) {
      setError('Пароли не совпадают');
      return;
    }
    // Минимальная длина пароля (дополнительная проверка к серверной валидации).
    if (password.length < 6) {
      setError('Пароль должен содержать минимум 6 символов');
      return;
    }

    setLoading(true);
    setError('');
    try {
      // POST /api/auth/register — регистрируем нового пользователя.
      // Тело запроса: { username, email, password } — без confirmPassword.
      // auth-service создаёт User с role=ROLE_CLIENT по умолчанию.
      await api.post('/auth/register', { username, email, password });

      // Регистрация успешна — показываем success сообщение.
      setSuccess(true);

      // setTimeout — через 2 секунды перенаправляем на страницу входа.
      // 2000 мс = 2 секунды (пользователь успевает прочитать сообщение).
      setTimeout(() => navigate('/login'), 2000);
    } catch (e: any) {
      // e.response?.data?.message — ошибка от сервера.
      // Пример: "Пользователь с таким именем уже существует" (из GlobalExceptionHandler).
      setError(e.response?.data?.message || 'Ошибка при регистрации. Возможно, пользователь уже существует.');
    } finally {
      setLoading(false);
    }
  };

  // Общий стиль полей ввода.
  const inputStyle: React.CSSProperties = {
    width: '100%',
    background: '#111',
    border: '1.5px solid #333',
    borderRadius: '8px',
    color: '#fff',
    padding: '0.8rem 1rem',
    fontSize: '0.95rem',
    outline: 'none',
    transition: 'border-color 0.2s',
    marginBottom: '1rem',
  };

  // Экран успешной регистрации — показывается вместо формы после success.
  if (success) {
    return (
      <div style={{ maxWidth: '420px', margin: '3rem auto', textAlign: 'center' }}>
        <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>✅</div>
        <h2 style={{ color: '#4caf50', marginBottom: '0.5rem' }}>Регистрация успешна!</h2>
        <p style={{ color: '#aaa' }}>Перенаправление на страницу входа...</p>
      </div>
    );
  }

  return (
    <div style={{
      maxWidth: '420px',
      margin: '3rem auto',
      background: '#1a1a1a',
      borderRadius: '12px',
      padding: '2.5rem',
      border: '1px solid #2a2a2a',
      boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
    }}>
      <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
        <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>🎬</div>
        <h1 style={{ fontSize: '1.6rem', fontWeight: 'bold', marginBottom: '0.3rem' }}>Регистрация</h1>
        <p style={{ color: '#666', fontSize: '0.9rem' }}>Создайте аккаунт в CinemaSystem</p>
      </div>

      <form onSubmit={handleSubmit}>
        {/* Поле: имя пользователя */}
        <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
          Имя пользователя
        </label>
        <input
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="Придумайте имя пользователя"
          autoComplete="username"
          style={inputStyle}
          // onFocus/onBlur — красная рамка при активном вводе, серая при неактивном
          onFocus={(e) => (e.target.style.borderColor = '#e50914')}
          onBlur={(e) => (e.target.style.borderColor = '#333')}
        />

        {/* Поле: email */}
        <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
          Email
        </label>
        <input
          type="email"  // type="email" — браузер валидирует формат email (user@domain.com)
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="Введите email"
          autoComplete="email"
          style={inputStyle}
          onFocus={(e) => (e.target.style.borderColor = '#e50914')}
          onBlur={(e) => (e.target.style.borderColor = '#333')}
        />

        {/* Поле: пароль */}
        <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
          Пароль
        </label>
        <input
          type="password"  // скрывает символы
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Минимум 6 символов"
          // new-password — подсказка браузеру НЕ предлагать сохранённые пароли,
          // а предложить сгенерировать новый
          autoComplete="new-password"
          style={inputStyle}
          onFocus={(e) => (e.target.style.borderColor = '#e50914')}
          onBlur={(e) => (e.target.style.borderColor = '#333')}
        />

        {/* Поле: подтверждение пароля */}
        <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
          Подтвердите пароль
        </label>
        <input
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          placeholder="Повторите пароль"
          autoComplete="new-password"
          style={inputStyle}
          onFocus={(e) => (e.target.style.borderColor = '#e50914')}
          onBlur={(e) => (e.target.style.borderColor = '#333')}
        />

        {/* Блок ошибки — условный рендеринг */}
        {error && (
          <div style={{
            background: '#2a0a0a',
            border: '1px solid #e50914',
            borderRadius: '6px',
            padding: '0.7rem 1rem',
            marginBottom: '1rem',
            color: '#ff6b6b',
            fontSize: '0.9rem',
          }}>
            {error}
          </div>
        )}

        {/* Кнопка отправки формы */}
        <button
          type="submit"
          disabled={loading}
          style={{
            width: '100%',
            background: loading ? '#555' : '#e50914',
            color: '#fff',
            border: 'none',
            borderRadius: '8px',
            padding: '0.9rem',
            fontSize: '1rem',
            fontWeight: '700',
            cursor: loading ? 'not-allowed' : 'pointer',
            marginBottom: '1.2rem',
          }}
        >
          {loading ? 'Регистрация...' : 'Зарегистрироваться'}
        </button>
      </form>

      {/* Ссылка на вход (если уже есть аккаунт) */}
      <div style={{ textAlign: 'center', color: '#666', fontSize: '0.9rem' }}>
        Уже есть аккаунт?{' '}
        <Link to="/login" style={{ color: '#e50914', fontWeight: '600' }}>
          Войти
        </Link>
      </div>
    </div>
  );
}
