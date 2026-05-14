// Outlet — компонент React Router v6: рендерит содержимое текущего дочернего маршрута.
//   В App.tsx: <Route path="/" element={<Layout />}> — Layout оборачивает все страницы.
//   <Outlet /> в Layout заменяется на текущую страницу (HomePage, MovieDetailPage и т.д.).
// Link — компонент для навигации (заменяет <a href>).
//   НЕ перезагружает страницу (SPA навигация через History API).
// useNavigate — хук для программной навигации (navigate('/') = переход на главную).
import { Outlet, Link, useNavigate } from 'react-router-dom';

// useAuth — хук для доступа к данным авторизации из AuthContext.
import { useAuth } from '../context/AuthContext';

// Layout — компонент навигационной панели (navbar) и основного контента.
// Рендерится один раз как обёртка для всех страниц.
export default function Layout() {
  // Деструктурируем нужные значения из контекста авторизации.
  // isAuthenticated — true если пользователь залогинен (user !== null).
  // isAdmin — user.role === 'ROLE_ADMIN' (для отображения ссылки Администратор).
  // isSeller — user.role === 'ROLE_SELLER' (для отображения ссылки Продавец).
  // logout — функция выхода из системы (очищает токены, сбрасывает стейт).
  const { isAuthenticated, isAdmin, isSeller, logout } = useAuth();

  // useNavigate — хук возвращающий функцию навигации.
  // navigate('/') — переход на главную страницу (SPA, без перезагрузки).
  const navigate = useNavigate();

  // handleLogout — обработчик кнопки "Выйти".
  // Сначала logout() (очищает данные), потом navigate('/') (редиректим на главную).
  const handleLogout = () => {
    logout();
    navigate('/');
  };

  // Стили описаны как React.CSSProperties объекты (типизированный inline CSS).
  // Преимущество: TypeScript проверяет имена свойств и типы значений.

  // navStyle — стиль навигационной панели (верхняя полоса).
  const navStyle: React.CSSProperties = {
    background: '#111',                // тёмный фон навбара
    borderBottom: '2px solid #e50914', // красная полоска снизу (фирменный цвет)
    padding: '0 2rem',                 // горизонтальные отступы
    display: 'flex',                   // flexbox для горизонтального расположения
    alignItems: 'center',              // вертикальное центрирование элементов
    justifyContent: 'space-between',   // лого слева, ссылки справа
    height: '64px',                    // фиксированная высота навбара
    position: 'sticky',                // навбар "прилипает" при прокрутке страницы
    top: 0,                            // прилипает к верху окна браузера
    zIndex: 1000,                      // поверх всего остального контента
  };

  // logoStyle — стиль логотипа "🎬 CinemaSystem".
  const logoStyle: React.CSSProperties = {
    fontSize: '1.5rem',
    fontWeight: 'bold',
    color: '#e50914',     // красный цвет (фирменный стиль)
    letterSpacing: '1px', // небольшой интервал между буквами
  };

  // navLinksStyle — контейнер для навигационных ссылок (правая часть навбара).
  const navLinksStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: '1.5rem', // расстояние между ссылками
  };

  // linkStyle — базовый стиль навигационной ссылки.
  const linkStyle: React.CSSProperties = {
    color: '#ccc',             // светло-серый цвет (не ярко-белый — меньше напряжение)
    fontSize: '0.95rem',
    transition: 'color 0.2s', // плавная смена цвета при hover (через CSS hover нужен отдельный класс)
    padding: '0.4rem 0.8rem',
    borderRadius: '4px',
  };

  // btnStyle — стиль кнопки "Регистрация" (заполненная красная кнопка).
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

  // outlineBtnStyle — стиль кнопки "Войти" и "Выйти" (контурная кнопка).
  const outlineBtnStyle: React.CSSProperties = {
    background: 'transparent',      // прозрачный фон
    color: '#e50914',                // красный текст
    border: '1.5px solid #e50914',  // красная рамка
    padding: '0.45rem 1.1rem',
    borderRadius: '4px',
    fontSize: '0.95rem',
    fontWeight: '600',
  };

  // mainStyle — стиль основного контента под навбаром.
  const mainStyle: React.CSSProperties = {
    minHeight: 'calc(100vh - 64px)', // минимальная высота = весь экран минус навбар 64px
    padding: '2rem',                 // внутренние отступы
    maxWidth: '1200px',              // максимальная ширина контента
    margin: '0 auto',               // центрирование (auto = равные боковые отступы)
  };

  return (
    <div>
      {/* nav — семантический HTML элемент навигации */}
      <nav style={navStyle}>
        {/* Лого — ссылка на главную страницу */}
        <Link to="/" style={logoStyle}>
          🎬 CinemaSystem
        </Link>

        {/* Навигационные ссылки */}
        <div style={navLinksStyle}>
          {/* Публичные ссылки — видны всем (авторизованным и нет) */}
          <Link to="/" style={linkStyle}>Афиша</Link>
          <Link to="/food" style={linkStyle}>Меню</Link>

          {/* Условный рендеринг — ссылки только для авторизованных */}
          {/* && оператор: если isAuthenticated = true → рендерим Link, иначе ничего */}
          {isAuthenticated && (
            <Link to="/orders" style={linkStyle}>Заказы</Link>
          )}
          {isAuthenticated && (
            <Link to="/profile" style={linkStyle}>Профиль</Link>
          )}
          {isAuthenticated && (
            <Link to="/support" style={linkStyle}>Поддержка</Link>
          )}

          {/* Ссылка администратора — только для ROLE_ADMIN */}
          {isAdmin && (
            {/* Spread + override: {...linkStyle, color: '#e50914'} —
                копируем все стили из linkStyle и переопределяем цвет на красный */}
            <Link to="/admin" style={{ ...linkStyle, color: '#e50914' }}>Администратор</Link>
          )}

          {/* Ссылка продавца — только для ROLE_SELLER */}
          {isSeller && (
            <Link to="/seller" style={{ ...linkStyle, color: '#f5a623' }}>Продавец</Link>
          )}

          {/* Кнопки авторизации — условный рендеринг */}
          {isAuthenticated ? (
            {/* Авторизован → кнопка "Выйти" */}
            <button onClick={handleLogout} style={outlineBtnStyle}>Выйти</button>
          ) : (
            {/* Не авторизован → кнопки "Войти" и "Регистрация" */}
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <Link to="/login">
                {/* Link обёртка вокруг button — переходим на /login при клике */}
                <button style={outlineBtnStyle}>Войти</button>
              </Link>
              <Link to="/register">
                <button style={btnStyle}>Регистрация</button>
              </Link>
            </div>
          )}
        </div>
      </nav>

      {/* main — семантический HTML элемент основного контента */}
      <main style={mainStyle}>
        {/* Outlet — КЛЮЧЕВОЙ компонент React Router.
            Здесь рендерится текущая страница (дочерний маршрут).
            При навигации на /movies/1 → Outlet заменяется на <MovieDetailPage />.
            Layout остаётся — перерисовывается только содержимое Outlet. */}
        <Outlet />
      </main>
    </div>
  );
}
