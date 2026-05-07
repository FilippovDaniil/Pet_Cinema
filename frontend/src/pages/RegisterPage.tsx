import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axios';

export default function RegisterPage() {
  const navigate = useNavigate();

  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !email.trim() || !password.trim()) {
      setError('Заполните все поля');
      return;
    }
    if (password !== confirmPassword) {
      setError('Пароли не совпадают');
      return;
    }
    if (password.length < 6) {
      setError('Пароль должен содержать минимум 6 символов');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await api.post('/auth/register', { username, email, password });
      setSuccess(true);
      setTimeout(() => navigate('/login'), 2000);
    } catch (e: any) {
      setError(e.response?.data?.message || 'Ошибка при регистрации. Возможно, пользователь уже существует.');
    } finally {
      setLoading(false);
    }
  };

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
          onFocus={(e) => (e.target.style.borderColor = '#e50914')}
          onBlur={(e) => (e.target.style.borderColor = '#333')}
        />

        <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
          Email
        </label>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="Введите email"
          autoComplete="email"
          style={inputStyle}
          onFocus={(e) => (e.target.style.borderColor = '#e50914')}
          onBlur={(e) => (e.target.style.borderColor = '#333')}
        />

        <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
          Пароль
        </label>
        <input
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          placeholder="Минимум 6 символов"
          autoComplete="new-password"
          style={inputStyle}
          onFocus={(e) => (e.target.style.borderColor = '#e50914')}
          onBlur={(e) => (e.target.style.borderColor = '#333')}
        />

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

      <div style={{ textAlign: 'center', color: '#666', fontSize: '0.9rem' }}>
        Уже есть аккаунт?{' '}
        <Link to="/login" style={{ color: '#e50914', fontWeight: '600' }}>
          Войти
        </Link>
      </div>
    </div>
  );
}
