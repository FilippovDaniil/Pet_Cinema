import { Outlet, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Layout() {
  const { isAuthenticated, isAdmin, isSeller, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/');
  };

  const navStyle: React.CSSProperties = {
    background: '#111',
    borderBottom: '2px solid #e50914',
    padding: '0 2rem',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: '64px',
    position: 'sticky',
    top: 0,
    zIndex: 1000,
  };

  const logoStyle: React.CSSProperties = {
    fontSize: '1.5rem',
    fontWeight: 'bold',
    color: '#e50914',
    letterSpacing: '1px',
  };

  const navLinksStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: '1.5rem',
  };

  const linkStyle: React.CSSProperties = {
    color: '#ccc',
    fontSize: '0.95rem',
    transition: 'color 0.2s',
    padding: '0.4rem 0.8rem',
    borderRadius: '4px',
  };

  const btnStyle: React.CSSProperties = {
    background: '#e50914',
    color: '#fff',
    border: 'none',
    padding: '0.5rem 1.2rem',
    borderRadius: '4px',
    fontSize: '0.95rem',
    fontWeight: '600',
    transition: 'background 0.2s',
  };

  const outlineBtnStyle: React.CSSProperties = {
    background: 'transparent',
    color: '#e50914',
    border: '1.5px solid #e50914',
    padding: '0.45rem 1.1rem',
    borderRadius: '4px',
    fontSize: '0.95rem',
    fontWeight: '600',
  };

  const mainStyle: React.CSSProperties = {
    minHeight: 'calc(100vh - 64px)',
    padding: '2rem',
    maxWidth: '1200px',
    margin: '0 auto',
  };

  return (
    <div>
      <nav style={navStyle}>
        <Link to="/" style={logoStyle}>
          🎬 CinemaSystem
        </Link>
        <div style={navLinksStyle}>
          <Link to="/" style={linkStyle}>Афиша</Link>
          <Link to="/food" style={linkStyle}>Меню</Link>
          {isAuthenticated && (
            <Link to="/orders" style={linkStyle}>Заказы</Link>
          )}
          {isAuthenticated && (
            <Link to="/profile" style={linkStyle}>Профиль</Link>
          )}
          {isAuthenticated && (
            <Link to="/support" style={linkStyle}>Поддержка</Link>
          )}
          {isAdmin && (
            <Link to="/admin" style={{ ...linkStyle, color: '#e50914' }}>Администратор</Link>
          )}
          {isSeller && (
            <Link to="/seller" style={{ ...linkStyle, color: '#f5a623' }}>Продавец</Link>
          )}
          {isAuthenticated ? (
            <button onClick={handleLogout} style={outlineBtnStyle}>Выйти</button>
          ) : (
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <Link to="/login">
                <button style={outlineBtnStyle}>Войти</button>
              </Link>
              <Link to="/register">
                <button style={btnStyle}>Регистрация</button>
              </Link>
            </div>
          )}
        </div>
      </nav>
      <main style={mainStyle}>
        <Outlet />
      </main>
    </div>
  );
}
