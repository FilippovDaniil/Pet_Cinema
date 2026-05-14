// useState — для локального стейта компонента.
// useEffect — побочный эффект: загрузка данных при монтировании.
import { useState, useEffect } from 'react';

// useParams — извлекает :movieId из URL (Route path="/movies/:movieId/sessions").
// useNavigate — программная навигация (navigate(-1) = кнопка "назад").
import { useParams, useNavigate } from 'react-router-dom';

import api from '../api/axios';

// TypeScript типы для данных с бэкенда.
import { Session, Movie, Hall, ExtraService } from '../types';

// formatTime — отображает только часы:минуты из ISO строки.
// toLocaleTimeString: 'ru-RU' формат даёт "12:00" вместо "12:00:00 AM".
function formatTime(dt: string) {
  return new Date(dt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

// formatDate — развёрнутое отображение даты: "понедельник, 5 мая".
// weekday:'long' — полное название дня недели (не "пн", а "понедельник").
function formatDate(dt: string) {
  return new Date(dt).toLocaleDateString('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' });
}

// groupByDate — группирует массив сеансов по датам.
// Результат: { "05.05.2025": [session1, session2], "06.05.2025": [session3] }.
// reduce: аккумулятор acc — объект, где ключ = дата, значение = массив сеансов.
// toLocaleDateString без параметров форматирования → просто "ДД.ММ.ГГГГ".
function groupByDate(sessions: Session[]): Record<string, Session[]> {
  return sessions.reduce((acc, s) => {
    const date = new Date(s.startTime).toLocaleDateString('ru-RU');
    if (!acc[date]) acc[date] = [];
    acc[date].push(s);
    return acc;
  }, {} as Record<string, Session[]>);
}

// hallTypeBadge — маппинг типов залов на отображаемые метки и цвета.
// Используется для цветных бейджей "VIP", "3D", "5D" на карточках сеансов.
const hallTypeBadge: Record<string, { label: string; color: string }> = {
  NORMAL: { label: 'Обычный', color: '#1a73e8' }, // синий
  VIP:    { label: 'VIP',     color: '#f5a623' }, // оранжевый
  THREE_D:{ label: '3D',      color: '#0d7a4e' }, // зелёный
  FIVE_D: { label: '5D',      color: '#7b1fa2' }, // фиолетовый
};

// SessionsPage — страница со списком сеансов для конкретного фильма.
// URL: /movies/:movieId/sessions (маршрут определён в App.tsx).
export default function SessionsPage() {
  // movieId — из URL параметра (строка, нужно parseInt для API).
  const { movieId } = useParams<{ movieId: string }>();
  const navigate = useNavigate();

  // Стейт страницы.
  const [movie, setMovie] = useState<Movie | null>(null);
  // halls: маппинг hallId → объект Hall (кеш, чтобы не загружать один зал несколько раз).
  const [sessions, setSessions] = useState<Session[]>([]);
  const [halls, setHalls] = useState<Record<number, Hall>>({});
  // extraServices: маппинг hallId → массив доп.услуг для этого зала.
  const [extraServices, setExtraServices] = useState<Record<number, ExtraService[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // useEffect с зависимостью [movieId] — перезапускается при изменении movieId.
  // В нормальном случае movieId не меняется за жизнь компонента.
  useEffect(() => {
    if (!movieId) return; // защита: если movieId undefined, выходим
    const fetchData = async () => {
      setLoading(true);
      try {
        // Promise.all — параллельно загружаем фильм и список сеансов.
        // Параллельность важна: два независимых запроса выполняются одновременно.
        const [movieRes, sessionsRes] = await Promise.all([
          api.get<Movie>(`/movies/${movieId}`),
          api.get<Session[]>(`/sessions`, { params: { movieId } }), // GET /api/sessions?movieId=X
        ]);
        setMovie(movieRes.data);

        // Фильтруем: показываем только активные сеансы (s.active === true).
        // Неактивные сеансы (активность снята администратором) скрываем.
        const activeSessions = (sessionsRes.data || []).filter((s) => s.active);
        setSessions(activeSessions);

        // [...new Set(...)] — получаем уникальные ID залов из всех сеансов.
        // Set удаляет дубликаты: если 5 сеансов в одном зале, зал загрузим 1 раз.
        const hallIds = [...new Set(activeSessions.map((s) => s.hallId))];
        const hallMap: Record<number, Hall> = {};
        const extraMap: Record<number, ExtraService[]> = {};

        // Для каждого уникального зала параллельно загружаем Hall + ExtraServices.
        // Promise.all внутри Promise.all — вложенная параллелизация.
        await Promise.all(
          hallIds.map(async (hallId) => {
            try {
              const [hallRes, extraRes] = await Promise.all([
                api.get<Hall>(`/halls/${hallId}`),
                // .catch(() => ({ data: [] })) — если нет доп.услуг → пустой массив, не ошибка.
                api.get<ExtraService[]>(`/halls/${hallId}/extra-services`).catch(() => ({ data: [] })),
              ]);
              hallMap[hallId] = hallRes.data;
              extraMap[hallId] = extraRes.data;
            } catch {} // если зал не найден — просто пропускаем
          })
        );
        setHalls(hallMap);
        setExtraServices(extraMap);
      } catch {
        setError('Не удалось загрузить сеансы');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [movieId]);

  // Экран загрузки.
  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '4rem', color: '#aaa' }}>
        <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>⏳</div>
        Загрузка сеансов...
      </div>
    );
  }

  // Экран ошибки.
  if (error) {
    return (
      <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '8px', padding: '1.5rem', color: '#ff6b6b' }}>
        {error}
      </div>
    );
  }

  // Группируем сеансы по датам для отображения секциями.
  const grouped = groupByDate(sessions);
  // Object.keys(grouped) — массив дат (строк) в порядке вставки в объект.
  const dates = Object.keys(grouped);

  return (
    <div>
      {/* Кнопка "Назад" — navigate(-1) аналог History.back() браузера. */}
      <button
        onClick={() => navigate(-1)}
        style={{ background: 'transparent', border: 'none', color: '#aaa', fontSize: '0.9rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}
      >
        ← Назад
      </button>

      {/* Заголовок с названием фильма — условный рендеринг (movie может быть null при ошибке). */}
      {movie && (
        <div style={{ marginBottom: '2rem' }}>
          <h1 style={{ fontSize: '1.8rem', fontWeight: 'bold', marginBottom: '0.3rem' }}>
            Сеансы: {movie.title}
          </h1>
          {/* movie.type?.replace('_', '') — "THREE_D" → "THREED" (убираем подчёркивание). */}
          <p style={{ color: '#aaa' }}>{movie.durationMinutes} мин · {movie.type?.replace('_', '')}</p>
        </div>
      )}

      {/* Пустое состояние: нет активных сеансов. */}
      {dates.length === 0 && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#666' }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🎭</div>
          Активных сеансов не найдено
        </div>
      )}

      {/* Итерация по датам — одна секция на каждую дату. */}
      {dates.map((date) => (
        <div key={date} style={{ marginBottom: '2.5rem' }}>
          {/* Заголовок секции — форматируем первый сеанс группы для получения даты. */}
          {/* textTransform:'capitalize' — делаем первую букву заглавной (день недели). */}
          <h2 style={{ fontSize: '1.1rem', color: '#e50914', marginBottom: '1rem', textTransform: 'capitalize' }}>
            {formatDate(grouped[date][0].startTime)}
          </h2>

          {/* Сетка карточек сеансов: auto-fill minmax(280px, 1fr) — адаптивная сетка. */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1rem' }}>
            {grouped[date].map((session) => {
              // Получаем данные зала из кеша (загружены выше).
              const hall = halls[session.hallId];
              // Доп.услуги для этого зала (или пустой массив если нет).
              const services = extraServices[session.hallId] || [];
              // Бейдж типа зала (NORMAL/VIP/3D/5D) или null если зал не загружен.
              const badge = hall ? (hallTypeBadge[hall.type] || { label: hall.type, color: '#444' }) : null;

              return (
                <div
                  key={session.id}
                  style={{
                    background: '#1a1a1a',
                    borderRadius: '10px',
                    padding: '1.2rem',
                    border: '1px solid #2a2a2a',
                    transition: 'border-color 0.2s',
                  }}
                  // Hover-эффект: красная рамка при наведении.
                  onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#e50914')}
                  onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#2a2a2a')}
                >
                  {/* Верхняя строка: время начала/конца + цена. */}
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.8rem' }}>
                    <div>
                      <div style={{ fontSize: '1.5rem', fontWeight: '700', color: '#fff' }}>
                        {formatTime(session.startTime)}
                      </div>
                      <div style={{ color: '#aaa', fontSize: '0.85rem' }}>
                        до {formatTime(session.endTime)}
                      </div>
                    </div>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontSize: '1.3rem', fontWeight: '700', color: '#e50914' }}>
                        {session.basePrice} ₽
                      </div>
                    </div>
                  </div>

                  {/* Информация о зале: название + бейдж типа + размерность. */}
                  {hall && (
                    <div style={{ marginBottom: '0.8rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.3rem' }}>
                        <span style={{ color: '#ddd', fontSize: '0.9rem' }}>{hall.name}</span>
                        {/* Цветной бейдж типа зала (VIP/3D/5D). */}
                        {badge && (
                          <span style={{
                            background: badge.color,
                            color: '#fff',
                            fontSize: '0.7rem',
                            fontWeight: '700',
                            padding: '2px 7px',
                            borderRadius: '3px',
                          }}>
                            {badge.label}
                          </span>
                        )}
                      </div>
                      {/* Размерность зала: рядов × мест в ряду. */}
                      <div style={{ color: '#666', fontSize: '0.8rem' }}>
                        {hall.rowsCount} рядов × {hall.seatsPerRow} мест
                      </div>
                    </div>
                  )}

                  {/* Список доп.услуг зала (если есть). */}
                  {services.length > 0 && (
                    <div style={{ marginBottom: '0.8rem' }}>
                      <div style={{ color: '#888', fontSize: '0.8rem', marginBottom: '0.3rem' }}>Дополнительные услуги:</div>
                      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.3rem' }}>
                        {services.map((s) => (
                          <span key={s.id} style={{ background: '#2a2a2a', color: '#aaa', fontSize: '0.75rem', padding: '2px 7px', borderRadius: '3px' }}>
                            {s.name} +{s.price}₽
                          </span>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Кнопка перехода к выбору места: /booking/:sessionId. */}
                  <button
                    onClick={() => navigate(`/booking/${session.id}`)}
                    style={{
                      width: '100%',
                      background: '#e50914',
                      color: '#fff',
                      border: 'none',
                      borderRadius: '6px',
                      padding: '0.65rem',
                      fontSize: '0.9rem',
                      fontWeight: '700',
                      marginTop: '0.5rem',
                    }}
                  >
                    Купить билет
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
