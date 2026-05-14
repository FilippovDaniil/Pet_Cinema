// React Router v6 импорты для маршрутизации.
// BrowserRouter — использует HTML5 History API (URL без # хэша: /movies/1, не /#/movies/1).
// Routes — контейнер маршрутов (замена Switch из React Router v5).
// Route — отдельный маршрут (path → компонент).
// Navigate — программная навигация (redirect).
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

// AuthProvider — обёртка предоставляющая глобальный стейт авторизации через Context.
// useAuth — хук для доступа к данным авторизации в любом компоненте.
import { AuthProvider, useAuth } from './context/AuthContext';

// Layout — компонент навбара + <Outlet /> (где рендерятся дочерние маршруты).
import Layout from './components/Layout';

// Все страницы приложения.
import HomePage from './pages/HomePage';
import MovieDetailPage from './pages/MovieDetailPage';
import SessionsPage from './pages/SessionsPage';
import BookingPage from './pages/BookingPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ProfilePage from './pages/ProfilePage';
import SupportPage from './pages/SupportPage';
import AdminPage from './pages/AdminPage';
import SellerPage from './pages/SellerPage';
import FoodMenuPage from './pages/FoodMenuPage';
import OrdersPage from './pages/OrdersPage';

// ProtectedRoute — компонент-обёртка для защиты маршрутов.
// Props:
//   children — компонент страницы который нужно защитить
//   roles — опциональный массив разрешённых ролей (если не указан — достаточно быть авторизованным)
function ProtectedRoute({ children, roles }: { children: React.ReactNode; roles?: string[] }) {
  // useAuth() — получаем данные из AuthContext (глобальный стейт авторизации).
  const { user, isAuthenticated } = useAuth();

  // Если не аутентифицирован → перенаправляем на /login.
  // <Navigate> — декларативный redirect (не window.location.href = ...).
  if (!isAuthenticated) return <Navigate to="/login" />;

  // Если указаны роли И пользователь не имеет нужной роли → redirect на главную.
  // roles.includes(user.role) — проверяет входит ли роль пользователя в список разрешённых.
  if (roles && user && !roles.includes(user.role)) return <Navigate to="/" />;

  // Аутентифицирован и имеет нужную роль → рендерим защищённый компонент.
  // <>{children}</> — Fragment (пустая обёртка без лишнего DOM элемента).
  return <>{children}</>;
}

// App — корневой компонент, определяет всю структуру приложения.
export default function App() {
  return (
    // AuthProvider — оборачиваем всё приложение в провайдер авторизации.
    // Делает user, accessToken, login, logout доступными через useAuth() в любом компоненте.
    <AuthProvider>
      {/* BrowserRouter — включает HTML5 History API для маршрутизации.
          Все Link и Navigate компоненты работают только внутри BrowserRouter. */}
      <BrowserRouter>
        <Routes>
          {/* Layout маршрут — обёртка для всех страниц (навбар + контент).
              path="/" — применяется ко всем дочерним маршрутам.
              element={<Layout />} — Layout рендерит <Outlet /> куда вставляются дочерние страницы. */}
          <Route path="/" element={<Layout />}>

            {/* index — маршрут по умолчанию для "/" (HomePage = афиша). */}
            <Route index element={<HomePage />} />

            {/* /movies/:id — детали фильма.
                :id — URL параметр, доступен через useParams() в компоненте. */}
            <Route path="movies/:id" element={<MovieDetailPage />} />

            {/* /sessions/:movieId — список сеансов по фильму. */}
            <Route path="sessions/:movieId" element={<SessionsPage />} />

            {/* /booking/:sessionId — страница бронирования места (ТОЛЬКО для CLIENT).
                ProtectedRoute roles={['ROLE_CLIENT']} — только клиенты могут бронировать.
                SELLER и ADMIN при попытке перейти → redirect на "/". */}
            <Route path="booking/:sessionId" element={
              <ProtectedRoute roles={['ROLE_CLIENT']}>
                <BookingPage />
              </ProtectedRoute>
            } />

            {/* /login и /register — публичные страницы (без ProtectedRoute). */}
            <Route path="login" element={<LoginPage />} />
            <Route path="register" element={<RegisterPage />} />

            {/* /profile — история заказов (только авторизованные, без ограничения ролью). */}
            <Route path="profile" element={
              <ProtectedRoute>
                <ProfilePage />
              </ProtectedRoute>
            } />

            {/* /support — чат техподдержки (только авторизованные). */}
            <Route path="support" element={
              <ProtectedRoute>
                <SupportPage />
              </ProtectedRoute>
            } />

            {/* /admin — панель администратора (ТОЛЬКО ROLE_ADMIN). */}
            <Route path="admin" element={
              <ProtectedRoute roles={['ROLE_ADMIN']}>
                <AdminPage />
              </ProtectedRoute>
            } />

            {/* /food — меню еды (публичная страница, без авторизации). */}
            <Route path="food" element={<FoodMenuPage />} />

            {/* /orders — список заказов текущего пользователя (авторизованные). */}
            <Route path="orders" element={
              <ProtectedRoute>
                <OrdersPage />
              </ProtectedRoute>
            } />

            {/* /seller — страница продавца для оформления заказов (ТОЛЬКО ROLE_SELLER). */}
            <Route path="seller" element={
              <ProtectedRoute roles={['ROLE_SELLER']}>
                <SellerPage />
              </ProtectedRoute>
            } />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
