import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { Session, Hall, ExtraService, Movie } from '../types';

export default function BookingPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();

  const [session, setSession] = useState<Session | null>(null);
  const [hall, setHall] = useState<Hall | null>(null);
  const [movie, setMovie] = useState<Movie | null>(null);
  const [extraServices, setExtraServices] = useState<ExtraService[]>([]);
  const [selectedSeat, setSelectedSeat] = useState<{ row: number; seat: number } | null>(null);
  const [selectedServices, setSelectedServices] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState<{ orderId: number; totalPrice: number } | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!sessionId) return;
    const fetchData = async () => {
      setLoading(true);
      try {
        const sessionRes = await api.get<Session>(`/sessions/${sessionId}`);
        setSession(sessionRes.data);

        const [hallRes, movieRes, extraRes] = await Promise.all([
          api.get<Hall>(`/halls/${sessionRes.data.hallId}`),
          api.get<Movie>(`/movies/${sessionRes.data.movieId}`),
          api.get<ExtraService[]>(`/halls/${sessionRes.data.hallId}/extra-services`).catch(() => ({ data: [] })),
        ]);
        setHall(hallRes.data);
        setMovie(movieRes.data);
        setExtraServices(extraRes.data);
      } catch {
        setError('Не удалось загрузить информацию о сеансе');
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [sessionId]);

  const toggleService = (id: number) => {
    setSelectedServices((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]
    );
  };

  const selectedServicesPrice = extraServices
    .filter((s) => selectedServices.includes(s.id))
    .reduce((sum, s) => sum + s.price, 0);

  const totalPrice = (session?.basePrice || 0) + selectedServicesPrice;

  const handleBook = async () => {
    if (!selectedSeat || !session) {
      setError('Пожалуйста, выберите место');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const payload = {
        sessionId: parseInt(sessionId!),
        seatRow: selectedSeat.row,
        seatNumber: selectedSeat.seat,
        extraServiceIds: selectedServices,
      };
      const res = await api.post('/orders/ticket', payload);
      setSuccess({ orderId: res.data.id, totalPrice: res.data.totalPrice });
    } catch (e: any) {
      setError(e.response?.data?.message || 'Ошибка при оформлении заказа');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '4rem', color: '#aaa' }}>
        <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>⏳</div>
        Загрузка...
      </div>
    );
  }

  if (error && !session) {
    return (
      <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '8px', padding: '1.5rem', color: '#ff6b6b' }}>
        {error}
      </div>
    );
  }

  if (success) {
    return (
      <div style={{ textAlign: 'center', padding: '4rem' }}>
        <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>✅</div>
        <h2 style={{ fontSize: '1.8rem', fontWeight: 'bold', color: '#4caf50', marginBottom: '0.8rem' }}>
          Заказ оформлен!
        </h2>
        <div style={{ background: '#0d2d0d', border: '1px solid #4caf50', borderRadius: '10px', padding: '1.5rem', maxWidth: '400px', margin: '0 auto 2rem' }}>
          <div style={{ color: '#aaa', marginBottom: '0.5rem' }}>Номер заказа: <strong style={{ color: '#fff' }}>#{success.orderId}</strong></div>
          <div style={{ color: '#aaa' }}>Итого: <strong style={{ color: '#e50914', fontSize: '1.2rem' }}>{success.totalPrice} ₽</strong></div>
          {selectedSeat && (
            <div style={{ color: '#aaa', marginTop: '0.5rem' }}>
              Место: ряд {selectedSeat.row}, место {selectedSeat.seat}
            </div>
          )}
        </div>
        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
          <button
            onClick={() => navigate('/profile')}
            style={{ background: '#e50914', color: '#fff', border: 'none', borderRadius: '8px', padding: '0.8rem 2rem', fontWeight: '700', fontSize: '1rem' }}
          >
            Мои заказы
          </button>
          <button
            onClick={() => navigate('/')}
            style={{ background: 'transparent', color: '#aaa', border: '1.5px solid #444', borderRadius: '8px', padding: '0.8rem 2rem', fontWeight: '600', fontSize: '1rem' }}
          >
            На главную
          </button>
        </div>
      </div>
    );
  }

  const rows = hall?.rowsCount || 5;
  const seatsPerRow = hall?.seatsPerRow || 10;

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      <button
        onClick={() => navigate(-1)}
        style={{ background: 'transparent', border: 'none', color: '#aaa', fontSize: '0.9rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}
      >
        ← Назад
      </button>

      <h1 style={{ fontSize: '1.8rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Выбор места</h1>

      {/* Session Info */}
      {session && movie && hall && (
        <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.2rem', marginBottom: '2rem', border: '1px solid #2a2a2a' }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1.5rem' }}>
            <div>
              <div style={{ color: '#aaa', fontSize: '0.8rem', marginBottom: '0.2rem' }}>ФИЛЬМ</div>
              <div style={{ fontWeight: '600' }}>{movie.title}</div>
            </div>
            <div>
              <div style={{ color: '#aaa', fontSize: '0.8rem', marginBottom: '0.2rem' }}>ЗАЛА</div>
              <div style={{ fontWeight: '600' }}>{hall.name}</div>
            </div>
            <div>
              <div style={{ color: '#aaa', fontSize: '0.8rem', marginBottom: '0.2rem' }}>ВРЕМЯ</div>
              <div style={{ fontWeight: '600' }}>
                {new Date(session.startTime).toLocaleString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}
              </div>
            </div>
            <div>
              <div style={{ color: '#aaa', fontSize: '0.8rem', marginBottom: '0.2rem' }}>ЦЕНА</div>
              <div style={{ fontWeight: '700', color: '#e50914' }}>{session.basePrice} ₽</div>
            </div>
          </div>
        </div>
      )}

      {/* Screen indicator */}
      <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
        <div style={{
          background: 'linear-gradient(to bottom, #e50914, #8a0000)',
          height: '6px',
          borderRadius: '3px',
          maxWidth: '400px',
          margin: '0 auto 0.5rem',
        }} />
        <span style={{ color: '#666', fontSize: '0.8rem' }}>ЭКРАН</span>
      </div>

      {/* Seat Grid */}
      <div style={{ overflowX: 'auto', marginBottom: '2rem' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'center', minWidth: 'fit-content' }}>
          {Array.from({ length: rows }, (_, rowIdx) => (
            <div key={rowIdx} style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
              <span style={{ color: '#555', fontSize: '0.75rem', width: '20px', textAlign: 'right' }}>
                {rowIdx + 1}
              </span>
              {Array.from({ length: seatsPerRow }, (_, seatIdx) => {
                const isSelected = selectedSeat?.row === rowIdx + 1 && selectedSeat?.seat === seatIdx + 1;
                return (
                  <button
                    key={seatIdx}
                    onClick={() => setSelectedSeat({ row: rowIdx + 1, seat: seatIdx + 1 })}
                    title={`Ряд ${rowIdx + 1}, место ${seatIdx + 1}`}
                    style={{
                      width: '28px',
                      height: '24px',
                      borderRadius: '4px 4px 2px 2px',
                      border: 'none',
                      background: isSelected ? '#e50914' : '#2a2a2a',
                      cursor: 'pointer',
                      transition: 'background 0.15s, transform 0.1s',
                      transform: isSelected ? 'scale(1.15)' : 'scale(1)',
                    }}
                    onMouseEnter={(e) => {
                      if (!isSelected) (e.currentTarget as HTMLButtonElement).style.background = '#444';
                    }}
                    onMouseLeave={(e) => {
                      if (!isSelected) (e.currentTarget as HTMLButtonElement).style.background = '#2a2a2a';
                    }}
                  />
                );
              })}
              <span style={{ color: '#555', fontSize: '0.75rem', width: '20px' }}>
                {rowIdx + 1}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Legend */}
      <div style={{ display: 'flex', gap: '1.5rem', justifyContent: 'center', marginBottom: '2rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <div style={{ width: '16px', height: '14px', background: '#2a2a2a', borderRadius: '3px 3px 1px 1px' }} />
          <span style={{ color: '#aaa', fontSize: '0.8rem' }}>Свободно</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <div style={{ width: '16px', height: '14px', background: '#e50914', borderRadius: '3px 3px 1px 1px' }} />
          <span style={{ color: '#aaa', fontSize: '0.8rem' }}>Выбрано</span>
        </div>
      </div>

      {selectedSeat && (
        <div style={{ textAlign: 'center', color: '#ddd', marginBottom: '1.5rem', fontSize: '0.95rem' }}>
          Выбрано: <strong style={{ color: '#fff' }}>Ряд {selectedSeat.row}, место {selectedSeat.seat}</strong>
        </div>
      )}

      {/* Extra Services */}
      {extraServices.length > 0 && (
        <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.2rem', marginBottom: '2rem', border: '1px solid #2a2a2a' }}>
          <h3 style={{ fontSize: '1rem', fontWeight: '600', marginBottom: '1rem' }}>Дополнительные услуги</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.7rem' }}>
            {extraServices.map((service) => (
              <label key={service.id} style={{ display: 'flex', alignItems: 'center', gap: '0.8rem', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  checked={selectedServices.includes(service.id)}
                  onChange={() => toggleService(service.id)}
                  style={{ width: '16px', height: '16px', accentColor: '#e50914' }}
                />
                <span style={{ color: '#ddd', flex: 1 }}>{service.name}</span>
                <span style={{ color: '#f5a623', fontWeight: '600' }}>+{service.price} ₽</span>
              </label>
            ))}
          </div>
        </div>
      )}

      {/* Total & Book */}
      <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.5rem', border: '1px solid #2a2a2a' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <span style={{ color: '#aaa' }}>Билет</span>
          <span>{session?.basePrice} ₽</span>
        </div>
        {selectedServicesPrice > 0 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
            <span style={{ color: '#aaa' }}>Доп. услуги</span>
            <span>+{selectedServicesPrice} ₽</span>
          </div>
        )}
        <div style={{ height: '1px', background: '#333', margin: '0.8rem 0' }} />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.2rem' }}>
          <span style={{ fontWeight: '700', fontSize: '1.1rem' }}>Итого</span>
          <span style={{ fontWeight: '700', fontSize: '1.4rem', color: '#e50914' }}>{totalPrice} ₽</span>
        </div>

        {error && (
          <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '6px', padding: '0.7rem 1rem', marginBottom: '1rem', color: '#ff6b6b', fontSize: '0.9rem' }}>
            {error}
          </div>
        )}

        <button
          onClick={handleBook}
          disabled={submitting || !selectedSeat}
          style={{
            width: '100%',
            background: (!selectedSeat || submitting) ? '#555' : '#e50914',
            color: '#fff',
            border: 'none',
            borderRadius: '8px',
            padding: '0.9rem',
            fontSize: '1rem',
            fontWeight: '700',
            cursor: (!selectedSeat || submitting) ? 'not-allowed' : 'pointer',
          }}
        >
          {submitting ? 'Оформление...' : selectedSeat ? `Оплатить ${totalPrice} ₽` : 'Выберите место'}
        </button>
      </div>
    </div>
  );
}
