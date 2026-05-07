import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { AuthResponse } from '../types';

export default function LoginPage() {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setError('Заполните все поля');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await api.post<AuthResponse>('/auth/login', { username, password });
      login(res.data.accessToken, res.data.refreshToken);
      navigate('/');
    } catch (e: any) {
      setError(e.response?.data?.message || 'Неверное имя пользователя или пароль');
    } finally {
      setLoading(false);
    }
  };

  const formContainerStyle: React.CSSProperties = {
    maxWidth: '420px',
    margin: '3rem auto',
    background: '#1a1a1a',
    borderRadius: '12px',
    padding: '2.5rem',
    border: '1px solid #2a2a2a',
    boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
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

  return (
    <div style={formContainerStyle}>
      <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
        <div style={{ fontSize: '2.5rem', marginBottom: '0.5rem' }}>🎬</div>
        <h1 style={{ fontSize: '1.6rem', fontWeight: 'bold', marginBottom: '0.3rem' }}>Вход в систему</h1>
        <p style={{ color: '#666', fontSize: '0.9rem' }}>Добро пожаловать в CinemaSystem</p>
      </div>

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '0.2rem' }}>
          <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
            Имя пользователя
          </label>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Введите имя пользователя"
            autoComplete="username"
            style={inputStyle}
            onFocus={(e) => (e.target.style.borderColor = '#e50914')}
            onBlur={(e) => (e.target.style.borderColor = '#333')}
          />
        </div>

        <div style={{ marginBottom: '0.5rem' }}>
          <label style={{ display: 'block', color: '#aaa', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
            Пароль
          </label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Введите пароль"
            autoComplete="current-password"
            style={inputStyle}
            onFocus={(e) => (e.target.style.borderColor = '#e50914')}
            onBlur={(e) => (e.target.style.borderColor = '#333')}
          />
        </div>

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
            transition: 'background 0.2s',
          }}
        >
          {loading ? 'Вход...' : 'Войти'}
        </button>
      </form>

      <div style={{ textAlign: 'center', color: '#666', fontSize: '0.9rem' }}>
        Нет аккаунта?{' '}
        <Link to="/register" style={{ color: '#e50914', fontWeight: '600' }}>
          Зарегистрироваться
        </Link>
      </div>
    </div>
  );
}
