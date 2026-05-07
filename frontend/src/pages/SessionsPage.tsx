import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { Session, Movie, Hall, ExtraService } from '../types';

function formatTime(dt: string) {
  return new Date(dt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

function formatDate(dt: string) {
  return new Date(dt).toLocaleDateString('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' });
}

function groupByDate(sessions: Session[]): Record<string, Session[]> {
  return sessions.reduce((acc, s) => {
    const date = new Date(s.startTime).toLocaleDateString('ru-RU');
    if (!acc[date]) acc[date] = [];
    acc[date].push(s);
    return acc;
  }, {} as Record<string, Session[]>);
}

const hallTypeBadge: Record<string, { label: string; color: string }> = {
  NORMAL: { label: 'Обычный', color: '#1a73e8' },
  VIP: { label: 'VIP', color: '#f5a623' },
  THREE_D: { label: '3D', color: '#0d7a4e' },
  FIVE_D: { label: '5D', color: '#7b1fa2' },
};

export default function SessionsPage() {
  const { movieId } = useParams<{ movieId: string }>();
  const navigate = useNavigate();

  const [movie, setMovie] = useState<Movie | null>(null);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [halls, setHalls] = useState<Record<number, Hall>>({});
  const [extraServices, setExtraServices] = useState<Record<number, ExtraService[]>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!movieId) return;
    const fetchData = async () => {
      setLoading(true);
      try {
        const [movieRes, sessionsRes] = await Promise.all([
          api.get<Movie>(`/movies/${movieId}`),
          api.get<Session[]>(`/sessions`, { params: { movieId } }),
        ]);
        setMovie(movieRes.data);
        const activeSessions = (sessionsRes.data || []).filter((s) => s.active);
        setSessions(activeSessions);

        // Fetch halls for unique hallIds
        const hallIds = [...new Set(activeSessions.map((s) => s.hallId))];
        const hallMap: Record<number, Hall> = {};
        const extraMap: Record<number, ExtraService[]> = {};

        await Promise.all(
          hallIds.map(async (hallId) => {
            try {
              const [hallRes, extraRes] = await Promise.all([
                api.get<Hall>(`/halls/${hallId}`),
                api.get<ExtraService[]>(`/halls/${hallId}/extra-services`).catch(() => ({ data: [] })),
              ]);
              hallMap[hallId] = hallRes.data;
              extraMap[hallId] = extraRes.data;
            } catch {}
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

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '4rem', color: '#aaa' }}>
        <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>⏳</div>
        Загрузка сеансов...
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '8px', padding: '1.5rem', color: '#ff6b6b' }}>
        {error}
      </div>
    );
  }

  const grouped = groupByDate(sessions);
  const dates = Object.keys(grouped);

  return (
    <div>
      <button
        onClick={() => navigate(-1)}
        style={{ background: 'transparent', border: 'none', color: '#aaa', fontSize: '0.9rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}
      >
        ← Назад
      </button>

      {movie && (
        <div style={{ marginBottom: '2rem' }}>
          <h1 style={{ fontSize: '1.8rem', fontWeight: 'bold', marginBottom: '0.3rem' }}>
            Сеансы: {movie.title}
          </h1>
          <p style={{ color: '#aaa' }}>{movie.durationMinutes} мин · {movie.type?.replace('_', '')}</p>
        </div>
      )}

      {dates.length === 0 && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#666' }}>
          <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🎭</div>
          Активных сеансов не найдено
        </div>
      )}

      {dates.map((date) => (
        <div key={date} style={{ marginBottom: '2.5rem' }}>
          <h2 style={{ fontSize: '1.1rem', color: '#e50914', marginBottom: '1rem', textTransform: 'capitalize' }}>
            {formatDate(grouped[date][0].startTime)}
          </h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '1rem' }}>
            {grouped[date].map((session) => {
              const hall = halls[session.hallId];
              const services = extraServices[session.hallId] || [];
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
                  onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#e50914')}
                  onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#2a2a2a')}
                >
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

                  {hall && (
                    <div style={{ marginBottom: '0.8rem' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '0.3rem' }}>
                        <span style={{ color: '#ddd', fontSize: '0.9rem' }}>{hall.name}</span>
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
                      <div style={{ color: '#666', fontSize: '0.8rem' }}>
                        {hall.rowsCount} рядов × {hall.seatsPerRow} мест
                      </div>
                    </div>
                  )}

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
