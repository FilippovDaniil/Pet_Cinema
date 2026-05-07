import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { Movie, Genre, PageResponse } from '../types';

const MOVIE_TYPES = [
  { value: '', label: 'Все форматы' },
  { value: 'TWO_D', label: '2D' },
  { value: 'THREE_D', label: '3D' },
  { value: 'FIVE_D', label: '5D' },
];

function StarRating({ rating }: { rating: number }) {
  return (
    <span style={{ color: '#f5a623', fontSize: '0.9rem' }}>
      {[1, 2, 3, 4, 5].map((s) => (
        <span key={s} style={{ color: s <= Math.round(rating) ? '#f5a623' : '#555' }}>★</span>
      ))}
      <span style={{ color: '#aaa', marginLeft: '0.4rem', fontSize: '0.8rem' }}>
        {rating ? rating.toFixed(1) : 'Нет оценок'}
      </span>
    </span>
  );
}

export default function HomePage() {
  const [movies, setMovies] = useState<Movie[]>([]);
  const [genres, setGenres] = useState<Genre[]>([]);
  const [selectedGenre, setSelectedGenre] = useState<number | null>(null);
  const [selectedType, setSelectedType] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  useEffect(() => {
    api.get<Genre[]>('/genres').then((r) => setGenres(r.data)).catch(() => {});
  }, []);

  useEffect(() => {
    fetchMovies();
  }, [selectedGenre, selectedType, page]);

  const fetchMovies = async () => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, string | number> = { page, size: 12 };
      if (selectedGenre) params.genreId = selectedGenre;
      if (selectedType) params.type = selectedType;
      const res = await api.get<PageResponse<Movie>>('/movies', { params });
      setMovies(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch {
      setError('Не удалось загрузить фильмы');
    } finally {
      setLoading(false);
    }
  };

  const handleGenreChange = (id: number | null) => {
    setSelectedGenre(id);
    setPage(0);
  };

  const handleTypeChange = (type: string) => {
    setSelectedType(type);
    setPage(0);
  };

  const typeBadgeColor: Record<string, string> = {
    TWO_D: '#1a73e8',
    THREE_D: '#0d7a4e',
    FIVE_D: '#7b1fa2',
  };

  return (
    <div>
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>
          Кинотека
        </h1>
        <p style={{ color: '#aaa' }}>Выберите фильм и забронируйте место</p>
      </div>

      {/* Filters */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem', marginBottom: '2rem', alignItems: 'center' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
          <button
            onClick={() => handleGenreChange(null)}
            style={{
              padding: '0.4rem 1rem',
              borderRadius: '20px',
              border: `1.5px solid ${selectedGenre === null ? '#e50914' : '#444'}`,
              background: selectedGenre === null ? '#e50914' : 'transparent',
              color: '#fff',
              fontSize: '0.85rem',
              fontWeight: selectedGenre === null ? '600' : '400',
            }}
          >
            Все жанры
          </button>
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

      {/* Loading / Error */}
      {loading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#aaa' }}>
          <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>⏳</div>
          Загрузка фильмов...
        </div>
      )}
      {error && (
        <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '8px', padding: '1rem', marginBottom: '1rem', color: '#ff6b6b' }}>
          {error}
        </div>
      )}

      {/* Movie Grid */}
      {!loading && (
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))',
          gap: '1.5rem',
          marginBottom: '2rem',
        }}>
          {movies.length === 0 && !error && (
            <div style={{ gridColumn: '1/-1', textAlign: 'center', color: '#aaa', padding: '3rem' }}>
              Фильмы не найдены
            </div>
          )}
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
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLDivElement).style.transform = 'translateY(-4px)';
                (e.currentTarget as HTMLDivElement).style.borderColor = '#e50914';
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLDivElement).style.transform = 'translateY(0)';
                (e.currentTarget as HTMLDivElement).style.borderColor = '#2a2a2a';
              }}
              onClick={() => navigate(`/movies/${movie.id}`)}
            >
              <div style={{ position: 'relative', aspectRatio: '2/3', overflow: 'hidden' }}>
                {movie.posterUrl ? (
                  <img
                    src={movie.posterUrl}
                    alt={movie.title}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    onError={(e) => {
                      (e.target as HTMLImageElement).src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="200" height="300"%3E%3Crect fill="%23222" width="200" height="300"/%3E%3Ctext fill="%23555" font-size="14" x="50%25" y="50%25" text-anchor="middle" dominant-baseline="middle"%3EНет постера%3C/text%3E%3C/svg%3E';
                    }}
                  />
                ) : (
                  <div style={{ width: '100%', height: '100%', background: '#222', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#555' }}>
                    Нет постера
                  </div>
                )}
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
                  {movie.type?.replace('_', '') || ''}
                </span>
              </div>
              <div style={{ padding: '0.8rem' }}>
                <h3 style={{ fontSize: '0.95rem', fontWeight: '600', marginBottom: '0.4rem', lineHeight: '1.3' }}>
                  {movie.title}
                </h3>
                <StarRating rating={movie.averageRating} />
                <div style={{ color: '#aaa', fontSize: '0.8rem', marginTop: '0.4rem' }}>
                  {movie.durationMinutes} мин
                </div>
                {movie.genres && movie.genres.length > 0 && (
                  <div style={{ marginTop: '0.4rem', display: 'flex', flexWrap: 'wrap', gap: '0.3rem' }}>
                    {movie.genres.slice(0, 2).map((g, i) => (
                      <span key={i} style={{ background: '#2a2a2a', color: '#aaa', fontSize: '0.7rem', padding: '2px 6px', borderRadius: '3px' }}>
                        {g}
                      </span>
                    ))}
                  </div>
                )}
                <button
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

      {/* Pagination */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', justifyContent: 'center', gap: '0.5rem', marginTop: '1.5rem' }}>
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
          {Array.from({ length: totalPages }, (_, i) => (
            <button
              key={i}
              onClick={() => setPage(i)}
              style={{
                background: i === page ? '#e50914' : '#1a1a1a',
                color: '#fff',
                border: `1px solid ${i === page ? '#e50914' : '#444'}`,
                borderRadius: '6px',
                padding: '0.5rem 0.8rem',
                minWidth: '40px',
              }}
            >
              {i + 1}
            </button>
          ))}
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
