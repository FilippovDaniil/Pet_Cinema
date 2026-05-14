// useState, useEffect — базовые React хуки.
// useState: хранит локальный стейт компонента (movies, filters, pagination).
// useEffect: выполняет побочные эффекты (API запросы при изменении фильтров).
import { useState, useEffect } from 'react';

// useNavigate — хук для программной навигации (navigate('/movies/1')).
import { useNavigate } from 'react-router-dom';

// api — настроенный axios экземпляр (baseURL='/api', JWT interceptor, auto-refresh).
import api from '../api/axios';

// TypeScript интерфейсы для type safety API ответов.
import { Movie, Genre, PageResponse } from '../types';

// POSTER_GRADIENTS — CSS градиенты для постеров без реального изображения.
// Record<string, string> — объект с ключами-строками и значениями-строками.
// Каждый тип фильма имеет свой цвет: 2D = синий, 3D = зелёный, 5D = фиолетовый.
const POSTER_GRADIENTS: Record<string, string> = {
  TWO_D:   'linear-gradient(160deg, #0f2027 0%, #203a43 50%, #2c5364 100%)',   // синий
  THREE_D: 'linear-gradient(160deg, #0a2e1a 0%, #0d5c32 50%, #11998e 100%)',   // зелёный
  FIVE_D:  'linear-gradient(160deg, #1a0533 0%, #4a1063 50%, #8b2fc9 100%)',   // фиолетовый
};

// getPosterGradient — возвращает градиент для типа фильма.
// ?? — nullish coalescing: если POSTER_GRADIENTS[type] = undefined → дефолтный синий.
function getPosterGradient(type: string): string {
  return POSTER_GRADIENTS[type] ?? 'linear-gradient(160deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)';
}

// MOVIE_TYPES — опции для select фильтра по формату.
// { value: '' } — пустая строка = "Все форматы" (без фильтра).
const MOVIE_TYPES = [
  { value: '', label: 'Все форматы' },
  { value: 'TWO_D', label: '2D' },
  { value: 'THREE_D', label: '3D' },
  { value: 'FIVE_D', label: '5D' },
];

// StarRating — компонент отображения звёзд рейтинга (только просмотр).
// rating — числовое значение (0.0 - 5.0).
// Math.round(rating) — округляем до целого для отображения заполненных звёзд.
function StarRating({ rating }: { rating: number }) {
  return (
    <span style={{ color: '#f5a623', fontSize: '0.9rem' }}>
      {/* [1,2,3,4,5].map — создаём 5 звёзд */}
      {[1, 2, 3, 4, 5].map((s) => (
        // s <= Math.round(rating) → заполненная ★ (#f5a623 = жёлтый), иначе пустая (#555 = серый)
        <span key={s} style={{ color: s <= Math.round(rating) ? '#f5a623' : '#555' }}>★</span>
      ))}
      <span style={{ color: '#aaa', marginLeft: '0.4rem', fontSize: '0.8rem' }}>
        {/* toFixed(1) — одна цифра после запятой (4.3), если 0 → "Нет оценок" */}
        {rating ? rating.toFixed(1) : 'Нет оценок'}
      </span>
    </span>
  );
}

// HomePage — страница-афиша: сетка фильмов с фильтрами и пагинацией.
export default function HomePage() {
  // movies — массив фильмов текущей страницы (из PageResponse.content).
  const [movies, setMovies] = useState<Movie[]>([]);
  // genres — список жанров для кнопок фильтра.
  const [genres, setGenres] = useState<Genre[]>([]);
  // selectedGenre — ID выбранного жанра (null = все жанры).
  const [selectedGenre, setSelectedGenre] = useState<number | null>(null);
  // selectedType — выбранный формат ('TWO_D', 'THREE_D', 'FIVE_D' или '' = все).
  const [selectedType, setSelectedType] = useState('');
  // page — текущая страница пагинации (0-based).
  const [page, setPage] = useState(0);
  // totalPages — общее количество страниц (из PageResponse.totalPages).
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  // useEffect — загружаем жанры один раз при монтировании.
  // [] — пустой массив зависимостей = только один раз.
  useEffect(() => {
    // GET /api/genres → массив Genre[].
    // .catch(() => {}) — игнорируем ошибку (жанры не критичны).
    api.get<Genre[]>('/genres').then((r) => setGenres(r.data)).catch(() => {});
  }, []);

  // useEffect — перезагружаем фильмы при изменении фильтров или страницы.
  // [selectedGenre, selectedType, page] — зависимости: эффект срабатывает при изменении любого.
  useEffect(() => {
    fetchMovies();
  }, [selectedGenre, selectedType, page]);

  // fetchMovies — async функция загрузки фильмов с применёнными фильтрами.
  const fetchMovies = async () => {
    setLoading(true);
    setError('');
    try {
      // Record<string, string | number> — объект query параметров.
      const params: Record<string, string | number> = { page, size: 12 };
      // Добавляем фильтры только если они выбраны (не null/пустая строка).
      if (selectedGenre) params.genreId = selectedGenre;
      if (selectedType) params.type = selectedType;

      // GET /api/movies?page=0&size=12&genreId=1&type=THREE_D
      // { params } — axios автоматически сериализует объект в query string.
      const res = await api.get<PageResponse<Movie>>('/movies', { params });
      setMovies(res.data.content);       // элементы текущей страницы
      setTotalPages(res.data.totalPages); // всего страниц
    } catch {
      setError('Не удалось загрузить фильмы');
    } finally {
      setLoading(false);
    }
  };

  // handleGenreChange — смена жанра и сброс на первую страницу.
  const handleGenreChange = (id: number | null) => {
    setSelectedGenre(id);
    setPage(0); // сбрасываем на страницу 0 при смене фильтра
  };

  // handleTypeChange — смена формата и сброс на первую страницу.
  const handleTypeChange = (type: string) => {
    setSelectedType(type);
    setPage(0);
  };

  // typeBadgeColor — цвета бейджей форматов на карточках.
  const typeBadgeColor: Record<string, string> = {
    TWO_D: '#1a73e8',   // синий
    THREE_D: '#0d7a4e', // зелёный
    FIVE_D: '#7b1fa2',  // фиолетовый
  };

  return (
    <div>
      {/* Заголовок страницы */}
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>
          Кинотека
        </h1>
        <p style={{ color: '#aaa' }}>Выберите фильм и забронируйте место</p>
      </div>

      {/* Блок фильтров: кнопки жанров + select формата */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem', marginBottom: '2rem', alignItems: 'center' }}>
        {/* Кнопки жанров */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
          {/* Кнопка "Все жанры" */}
          <button
            onClick={() => handleGenreChange(null)}
            style={{
              padding: '0.4rem 1rem',
              borderRadius: '20px',
              // Активная кнопка: красный фон и рамка; неактивная: прозрачный фон и серая рамка
              border: `1.5px solid ${selectedGenre === null ? '#e50914' : '#444'}`,
              background: selectedGenre === null ? '#e50914' : 'transparent',
              color: '#fff',
              fontSize: '0.85rem',
              fontWeight: selectedGenre === null ? '600' : '400',
            }}
          >
            Все жанры
          </button>
          {/* Кнопки для каждого жанра */}
          {genres.map((g) => (
            <button
              key={g.id}
              onClick={() => handleGenreChange(g.id)}
              style={{
                padding: '0.4rem 1rem',
                borderRadius: '20px',
                border: `1.5px solid ${selectedGenre === g.id ? '#e50914' : '#444'}`,
                background: selectedGenre === g.id ? '#e50914' : 'transparent',
                color: '#fff',
                fontSize: '0.85rem',
                fontWeight: selectedGenre === g.id ? '600' : '400',
              }}
            >
              {g.name}
            </button>
          ))}
        </div>

        {/* Select для фильтра по формату (2D/3D/5D) */}
        <select
          value={selectedType}
          onChange={(e) => handleTypeChange(e.target.value)}
          style={{
            background: '#1a1a1a',
            color: '#fff',
            border: '1.5px solid #444',
            borderRadius: '8px',
            padding: '0.4rem 0.8rem',
            fontSize: '0.9rem',
          }}
        >
          {MOVIE_TYPES.map((t) => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>
      </div>

      {/* Индикатор загрузки */}
      {loading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#aaa' }}>
          <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>⏳</div>
          Загрузка фильмов...
        </div>
      )}

      {/* Блок ошибки */}
      {error && (
        <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '8px', padding: '1rem', marginBottom: '1rem', color: '#ff6b6b' }}>
          {error}
        </div>
      )}

      {/* CSS Grid сетка карточек фильмов */}
      {!loading && (
        <div style={{
          display: 'grid',
          // auto-fill + minmax(200px, 1fr) — адаптивная сетка:
          // браузер создаёт максимум колонок шириной минимум 200px, каждая занимает равную долю.
          gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
          gap: '1.5rem',
          marginBottom: '2rem',
        }}>
          {/* Пустое состояние — нет фильмов */}
          {movies.length === 0 && !error && (
            // gridColumn: '1/-1' — занимает все колонки (от первой до последней)
            <div style={{ gridColumn: '1/-1', textAlign: 'center', color: '#aaa', padding: '3rem' }}>
              Фильмы не найдены
            </div>
          )}

          {/* Карточки фильмов */}
          {movies.map((movie) => (
            <div
              key={movie.id}
              style={{
                background: '#1a1a1a',
                borderRadius: '10px',
                overflow: 'hidden',
                border: '1px solid #2a2a2a',
                transition: 'transform 0.2s, border-color 0.2s',
                cursor: 'pointer',
              }}
              // onMouseEnter/Leave — hover эффект: поднятие карточки и красная рамка
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLDivElement).style.transform = 'translateY(-4px)';
                (e.currentTarget as HTMLDivElement).style.borderColor = '#e50914';
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLDivElement).style.transform = 'translateY(0)';
                (e.currentTarget as HTMLDivElement).style.borderColor = '#2a2a2a';
              }}
              // Клик на карточку → детали фильма
              onClick={() => navigate(`/movies/${movie.id}`)}
            >
              {/* Постер: соотношение 2:3 (стандарт кинопостера) */}
              <div style={{ position: 'relative', aspectRatio: '2/3', overflow: 'hidden', background: getPosterGradient(movie.type) }}>
                {/* Fallback: эмодзи + название (видно если нет изображения или оно не загрузилось) */}
                <div style={{
                  position: 'absolute', inset: 0,
                  display: 'flex', flexDirection: 'column',
                  alignItems: 'center', justifyContent: 'center',
                  gap: '0.5rem', padding: '0.8rem',
                }}>
                  <span style={{ fontSize: '2.5rem', lineHeight: 1 }}>🎬</span>
                  <span style={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.75rem', textAlign: 'center', lineHeight: 1.3 }}>
                    {movie.title}
                  </span>
                </div>
                {/* Реальный постер поверх fallback (скрывает его при загрузке) */}
                {movie.posterUrl && (
                  <img
                    src={movie.posterUrl}
                    alt={movie.title}
                    style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
                    // onError — если изображение не загрузилось → скрываем (виден fallback)
                    onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                  />
                )}
                {/* Бейдж формата (2D/3D/5D) в правом верхнем углу */}
                <span style={{
                  position: 'absolute',
                  top: '8px',
                  right: '8px',
                  background: typeBadgeColor[movie.type] || '#444',
                  color: '#fff',
                  fontSize: '0.7rem',
                  fontWeight: '700',
                  padding: '2px 8px',
                  borderRadius: '4px',
                }}>
                  {/* movie.type?.replace('_', '') — убираем подчёркивание: TWO_D → TWOD */}
                  {movie.type?.replace('_', '') || ''}
                </span>
              </div>

              {/* Информация о фильме под постером */}
              <div style={{ padding: '0.8rem' }}>
                <h3 style={{ fontSize: '0.95rem', fontWeight: '600', marginBottom: '0.4rem', lineHeight: '1.3' }}>
                  {movie.title}
                </h3>
                <StarRating rating={movie.averageRating} />
                <div style={{ color: '#aaa', fontSize: '0.8rem', marginTop: '0.4rem' }}>
                  {movie.durationMinutes} мин
                </div>
                {/* Жанры — показываем максимум 2 (slice(0, 2)) */}
                {movie.genres && movie.genres.length > 0 && (
                  <div style={{ marginTop: '0.4rem', display: 'flex', flexWrap: 'wrap', gap: '0.3rem' }}>
                    {movie.genres.slice(0, 2).map((g, i) => (
                      <span key={i} style={{ background: '#2a2a2a', color: '#aaa', fontSize: '0.7rem', padding: '2px 6px', borderRadius: '3px' }}>
                        {g}
                      </span>
                    ))}
                  </div>
                )}
                {/* Кнопка "Подробнее" */}
                <button
                  // e.stopPropagation() — предотвращаем всплытие клика до родительского div
                  // (иначе клик по кнопке сработает ДВА раза: кнопка + onClick карточки)
                  onClick={(e) => { e.stopPropagation(); navigate(`/movies/${movie.id}`); }}
                  style={{
                    marginTop: '0.8rem',
                    width: '100%',
                    background: '#e50914',
                    color: '#fff',
                    border: 'none',
                    borderRadius: '6px',
                    padding: '0.5rem',
                    fontSize: '0.85rem',
                    fontWeight: '600',
                  }}
                >
                  Подробнее
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Пагинация — показывается только если страниц больше одной */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', gap: '0.5rem', marginTop: '1.5rem' }}>
          {/* Кнопка "Назад" — disabled на первой странице */}
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            style={{
              background: page === 0 ? '#222' : '#e50914',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
              padding: '0.5rem 1rem',
              opacity: page === 0 ? 0.5 : 1,
            }}
          >
            ← Назад
          </button>

          {/* Номера страниц */}
          {/* Array.from({ length: totalPages }) — создаём массив нужной длины */}
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i}
              onClick={() => setPage(i)}
              style={{
                background: i === page ? '#e50914' : '#1a1a1a', // активная страница — красная
                color: '#fff',
                border: `1px solid ${i === page ? '#e50914' : '#444'}`,
                borderRadius: '6px',
                padding: '0.5rem 0.8rem',
                minWidth: '40px',
              }}
            >
              {i + 1} {/* +1 т.к. page 0-based, показываем 1-based */}
            </button>
          ))}

          {/* Кнопка "Вперёд" — disabled на последней странице */}
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            style={{
              background: page >= totalPages - 1 ? '#222' : '#e50914',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
              padding: '0.5rem 1rem',
              opacity: page >= totalPages - 1 ? 0.5 : 1,
            }}
          >
            Вперёд →
          </button>
        </div>
      )}
    </div>
  );
}
