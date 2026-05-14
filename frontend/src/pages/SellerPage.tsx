import { useState, useEffect } from 'react';
import api from '../api/axios';
import { Movie, Session, Hall, ExtraService, FoodItem, Order } from '../types';

// Tab — тип вкладок: продать билет / продать еду / мои заказы.
type Tab = 'ticket' | 'food' | 'orders';
// TicketStep — шаги мастера продажи билета (1..6).
type TicketStep = 1 | 2 | 3 | 4 | 5 | 6;

const TABS: { key: Tab; label: string }[] = [
  { key: 'ticket', label: 'Продать билет' },
  { key: 'food',   label: 'Продать еду' },
  { key: 'orders', label: 'Мои заказы' },
];

// Общие стили — на уровне модуля для переиспользования во всех sub-компонентах.
const inputStyle: React.CSSProperties = {
  width: '100%',
  background: '#111',
  border: '1.5px solid #333',
  borderRadius: '6px',
  color: '#fff',
  padding: '0.7rem 0.9rem',
  fontSize: '0.9rem',
  marginBottom: '0.7rem',
};

const btnPrimary: React.CSSProperties = {
  background: '#e50914',
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  padding: '0.6rem 1.4rem',
  fontWeight: '700',
  fontSize: '0.9rem',
  cursor: 'pointer',
};

// ==================== SELL TICKET TAB ====================
// SellTicketTab — 6-шаговый мастер продажи билета продавцом.
// Шаги: 1. ID клиента → 2. Фильм → 3. Сеанс → 4. Место → 5. Доп.услуги → 6. Подтверждение.
function SellTicketTab() {
  const [step, setStep] = useState<TicketStep>(1);  // текущий шаг
  const [clientId, setClientId] = useState('');     // ID клиента (ввод продавцом)
  const [movies, setMovies] = useState<Movie[]>([]);
  const [sessions, setSessions] = useState<Session[]>([]);
  // halls: маппинг hallId → Hall (кеш для отображения названий залов).
  const [halls, setHalls] = useState<Record<number, Hall>>({});
  const [extraServices, setExtraServices] = useState<ExtraService[]>([]);
  const [selectedMovie, setSelectedMovie] = useState<Movie | null>(null);
  const [selectedSession, setSelectedSession] = useState<Session | null>(null);
  // selectedSeat: { row, seat } — выбранное место в зале (1-based).
  const [selectedSeat, setSelectedSeat] = useState<{ row: number; seat: number } | null>(null);
  const [selectedServices, setSelectedServices] = useState<number[]>([]); // ID выбранных услуг
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  // success: { orderId, totalPrice } — экран успеха после создания заказа.
  const [success, setSuccess] = useState<{ orderId: number; totalPrice: number } | null>(null);

  // fetchMovies — загружает все фильмы (вызывается на шаге 1 после валидации clientId).
  const fetchMovies = async () => {
    setLoading(true);
    try {
      const res = await api.get('/movies', { params: { size: 100 } });
      setMovies(res.data.content || res.data || []);
    } catch { setError('Ошибка загрузки фильмов'); } finally { setLoading(false); }
  };

  // fetchSessions — загружает активные сеансы для выбранного фильма.
  // Также загружает данные о залах (для отображения названий).
  const fetchSessions = async (movieId: number) => {
    setLoading(true);
    try {
      const res = await api.get<Session[]>('/sessions', { params: { movieId } });
      const active = (res.data || []).filter((s) => s.active);
      setSessions(active);

      // Уникальные hallId из активных сеансов.
      const hallIds = [...new Set(active.map((s) => s.hallId))];
      const map: Record<number, Hall> = {};
      await Promise.all(hallIds.map(async (id) => {
        try {
          const r = await api.get<Hall>(`/halls/${id}`);
          map[id] = r.data;
        } catch { }
      }));
      setHalls(map);
    } catch { setError('Ошибка загрузки сеансов'); } finally { setLoading(false); }
  };

  // fetchExtraServices — загружает доп.услуги зала выбранного сеанса.
  const fetchExtraServices = async (hallId: number) => {
    try {
      const res = await api.get<ExtraService[]>(`/halls/${hallId}/extra-services`);
      setExtraServices(res.data || []);
    } catch { setExtraServices([]); }
  };

  // handleStep1 — валидация ID клиента и переход на шаг 2.
  const handleStep1 = () => {
    // isNaN(parseInt(clientId)) — проверяем что введено число.
    if (!clientId.trim() || isNaN(parseInt(clientId))) {
      setError('Введите корректный ID клиента');
      return;
    }
    setError('');
    fetchMovies();   // параллельно начинаем загружать фильмы
    setStep(2);
  };

  // handleSelectMovie — выбор фильма на шаге 2, переход на шаг 3.
  const handleSelectMovie = (movie: Movie) => {
    setSelectedMovie(movie);
    fetchSessions(movie.id);
    setStep(3);
  };

  // handleSelectSession — выбор сеанса на шаге 3, переход на шаг 4.
  const handleSelectSession = (session: Session) => {
    setSelectedSession(session);
    fetchExtraServices(session.hallId);
    setStep(4);
  };

  // handleSelectSeat — выбор места на шаге 4 (обновляет selectedSeat без авто-перехода).
  const handleSelectSeat = (seat: { row: number; seat: number }) => {
    setSelectedSeat(seat);
    setStep(5);
  };

  const toggleService = (id: number) => {
    setSelectedServices((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]
    );
  };

  // Сумма выбранных доп.услуг.
  const servicesPrice = extraServices
    .filter((s) => selectedServices.includes(s.id))
    .reduce((sum, s) => sum + s.price, 0);

  const totalPrice = (selectedSession?.basePrice || 0) + servicesPrice;

  // confirmOrder — финальный POST на шаге 6.
  const confirmOrder = async () => {
    if (!selectedSeat || !selectedSession) return;
    setError('');
    setLoading(true);
    try {
      // POST /api/orders/ticket/by-seller — продавец создаёт заказ сразу PAID.
      // Kafka payment-simulator НЕ вызывается (нет PENDING → payment-request цикла).
      // Ticket создаётся немедленно.
      const res = await api.post('/orders/ticket/by-seller', {
        clientId:        parseInt(clientId),
        sessionId:       selectedSession.id,
        seatRow:         selectedSeat.row,
        seatNumber:      selectedSeat.seat,
        extraServiceIds: selectedServices,
      });
      setSuccess({ orderId: res.data.id, totalPrice: res.data.totalPrice });
    } catch (e: any) {
      setError(e.response?.data?.message || 'Ошибка создания заказа');
    } finally {
      setLoading(false);
    }
  };

  // reset — сбрасывает всё состояние мастера на начальный шаг 1.
  const reset = () => {
    setStep(1);
    setClientId('');
    setSelectedMovie(null);
    setSelectedSession(null);
    setSelectedSeat(null);
    setSelectedServices([]);
    setSuccess(null);
    setError('');
    setSessions([]);
  };

  // Экран успеха — показывается вместо мастера.
  if (success) {
    return (
      <div style={{ textAlign: 'center', padding: '3rem' }}>
        <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>✅</div>
        <h2 style={{ color: '#4caf50', marginBottom: '0.5rem' }}>Заказ оформлен!</h2>
        <div style={{ background: '#0d2d0d', border: '1px solid #4caf50', borderRadius: '10px', padding: '1.5rem', maxWidth: '350px', margin: '0 auto 2rem' }}>
          <div style={{ color: '#aaa', marginBottom: '0.4rem' }}>Заказ #{success.orderId}</div>
          <div style={{ color: '#e50914', fontSize: '1.5rem', fontWeight: '700' }}>{success.totalPrice} ₽</div>
          {selectedSeat && <div style={{ color: '#aaa', marginTop: '0.5rem' }}>Ряд {selectedSeat.row}, место {selectedSeat.seat}</div>}
        </div>
        <button onClick={reset} style={btnPrimary}>Оформить новый заказ</button>
      </div>
    );
  }

  // hall — данные зала для выбранного сеанса (для шага 4 — сетка мест).
  const hall = selectedSession ? halls[selectedSession.hallId] : null;

  return (
    <div style={{ maxWidth: '700px' }}>
      {/* Индикатор шагов: 6 кружков с числами, соединённых линиями.
          Кружки окрашены красным для пройденных/текущих шагов. */}
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '2rem', overflowX: 'auto' }}>
        {[1, 2, 3, 4, 5, 6].map((s) => (
          <div key={s} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <div style={{
              width: '28px',
              height: '28px',
              borderRadius: '50%',
              background: step >= s ? '#e50914' : '#2a2a2a', // красный если пройден/текущий
              color: '#fff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '0.8rem',
              fontWeight: '700',
              flexShrink: 0,
            }}>
              {s}
            </div>
            {/* Горизонтальная линия между кружками: красная если шаг уже пройден. */}
            {s < 6 && <div style={{ width: '20px', height: '2px', background: step > s ? '#e50914' : '#2a2a2a', flexShrink: 0 }} />}
          </div>
        ))}
      </div>

      {/* Блок ошибки. */}
      {error && (
        <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '6px', padding: '0.7rem 1rem', marginBottom: '1rem', color: '#ff6b6b', fontSize: '0.9rem' }}>
          {error}
        </div>
      )}

      {/* Шаг 1: Ввод ID клиента. */}
      {step === 1 && (
        <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.5rem', border: '1px solid #2a2a2a' }}>
          <h3 style={{ marginBottom: '1rem' }}>Шаг 1: ID клиента</h3>
          <input
            style={inputStyle}
            placeholder="Введите ID клиента (число)"
            type="number"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleStep1()} // Enter = Далее
          />
          <button onClick={handleStep1} style={btnPrimary}>Далее →</button>
        </div>
      )}

      {/* Шаг 2: Выбор фильма. */}
      {step === 2 && (
        <div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <h3>Шаг 2: Выберите фильм</h3>
            <span style={{ color: '#aaa', fontSize: '0.85rem' }}>Клиент #{clientId}</span>
          </div>
          {loading && <div style={{ color: '#aaa', padding: '1rem' }}>⏳ Загрузка...</div>}
          {/* Сетка карточек фильмов с poster изображениями. */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: '0.8rem' }}>
            {movies.map((movie) => (
              <div
                key={movie.id}
                onClick={() => handleSelectMovie(movie)}
                style={{ background: '#1a1a1a', borderRadius: '8px', padding: '1rem', border: '1px solid #2a2a2a', cursor: 'pointer', transition: 'border-color 0.2s' }}
                onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#e50914')}
                onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#2a2a2a')}
              >
                {movie.posterUrl && (
                  <img src={movie.posterUrl} alt={movie.title} style={{ width: '100%', aspectRatio: '2/3', objectFit: 'cover', borderRadius: '4px', marginBottom: '0.5rem' }} />
                )}
                <div style={{ fontWeight: '600', fontSize: '0.85rem', marginBottom: '0.3rem' }}>{movie.title}</div>
                <div style={{ color: '#aaa', fontSize: '0.8rem' }}>{movie.durationMinutes} мин</div>
              </div>
            ))}
          </div>
          <button onClick={() => setStep(1)} style={{ ...btnPrimary, background: 'transparent', border: '1px solid #444', color: '#aaa', marginTop: '1rem' }}>
            ← Назад
          </button>
        </div>
      )}

      {/* Шаг 3: Выбор сеанса. */}
      {step === 3 && selectedMovie && (
        <div>
          <h3 style={{ marginBottom: '1rem' }}>Шаг 3: Выберите сеанс для "{selectedMovie.title}"</h3>
          {loading && <div style={{ color: '#aaa', padding: '1rem' }}>⏳ Загрузка...</div>}
          {sessions.length === 0 && !loading && (
            <div style={{ color: '#666', padding: '2rem', textAlign: 'center' }}>Нет активных сеансов</div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
            {sessions.map((session) => {
              const h = halls[session.hallId];
              return (
                <div
                  key={session.id}
                  onClick={() => handleSelectSession(session)}
                  style={{ background: '#1a1a1a', borderRadius: '8px', padding: '1rem 1.2rem', border: '1px solid #2a2a2a', cursor: 'pointer', display: 'flex', justifyContent: 'space-between', alignItems: 'center', transition: 'border-color 0.2s' }}
                  onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#e50914')}
                  onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#2a2a2a')}
                >
                  <div>
                    <div style={{ fontWeight: '600' }}>
                      {new Date(session.startTime).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                    </div>
                    {/* h?.name — название зала из кеша halls. */}
                    <div style={{ color: '#aaa', fontSize: '0.85rem' }}>{h?.name || `Зал #${session.hallId}`}</div>
                  </div>
                  <div style={{ color: '#e50914', fontWeight: '700', fontSize: '1.1rem' }}>{session.basePrice} ₽</div>
                </div>
              );
            })}
          </div>
          <button onClick={() => setStep(2)} style={{ ...btnPrimary, background: 'transparent', border: '1px solid #444', color: '#aaa', marginTop: '1rem' }}>
            ← Назад
          </button>
        </div>
      )}

      {/* Шаг 4: Выбор места в зале. */}
      {step === 4 && selectedSession && hall && (
        <div>
          <h3 style={{ marginBottom: '1rem' }}>Шаг 4: Выберите место</h3>
          {/* Индикатор экрана. */}
          <div style={{ textAlign: 'center', marginBottom: '1rem' }}>
            <div style={{ background: 'linear-gradient(to bottom, #e50914, #8a0000)', height: '5px', borderRadius: '3px', maxWidth: '350px', margin: '0 auto 0.5rem' }} />
            <span style={{ color: '#555', fontSize: '0.75rem' }}>ЭКРАН</span>
          </div>
          <div style={{ overflowX: 'auto', marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '5px', alignItems: 'center', minWidth: 'fit-content' }}>
              {/* Генерация строк рядов: Array.from({ length: rowsCount }). */}
              {Array.from({ length: hall.rowsCount }, (_, rowIdx) => (
                <div key={rowIdx} style={{ display: 'flex', gap: '5px', alignItems: 'center' }}>
                  <span style={{ color: '#555', fontSize: '0.7rem', width: '18px', textAlign: 'right' }}>{rowIdx + 1}</span>
                  {/* Генерация кнопок мест в ряду. */}
                  {Array.from({ length: hall.seatsPerRow }, (_, seatIdx) => {
                    const isSelected = selectedSeat?.row === rowIdx + 1 && selectedSeat?.seat === seatIdx + 1;
                    return (
                      <button
                        key={seatIdx}
                        onClick={() => setSelectedSeat({ row: rowIdx + 1, seat: seatIdx + 1 })}
                        style={{
                          width: '26px',
                          height: '22px',
                          borderRadius: '3px 3px 2px 2px',
                          border: 'none',
                          background: isSelected ? '#e50914' : '#2a2a2a',
                          cursor: 'pointer',
                          transition: 'background 0.15s',
                        }}
                      />
                    );
                  })}
                </div>
              ))}
            </div>
          </div>
          {selectedSeat && (
            <div style={{ textAlign: 'center', marginBottom: '1rem', color: '#ddd' }}>
              Выбрано: ряд {selectedSeat.row}, место {selectedSeat.seat}
            </div>
          )}
          <div style={{ display: 'flex', gap: '0.7rem' }}>
            {/* "Далее" — активна только если место выбрано. */}
            <button
              onClick={() => selectedSeat && setStep(5)}
              disabled={!selectedSeat}
              style={{ ...btnPrimary, opacity: !selectedSeat ? 0.5 : 1, cursor: !selectedSeat ? 'not-allowed' : 'pointer' }}
            >
              Далее →
            </button>
            <button onClick={() => setStep(3)} style={{ ...btnPrimary, background: 'transparent', border: '1px solid #444', color: '#aaa' }}>
              ← Назад
            </button>
          </div>
        </div>
      )}

      {/* Шаг 5: Выбор доп.услуг. */}
      {step === 5 && (
        <div>
          <h3 style={{ marginBottom: '1rem' }}>Шаг 5: Дополнительные услуги</h3>
          {extraServices.length === 0 ? (
            <div style={{ color: '#666', marginBottom: '1rem' }}>Нет дополнительных услуг</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.7rem', marginBottom: '1rem' }}>
              {extraServices.map((service) => (
                // label оборачивает checkbox — клик по тексту тоже переключает.
                <label key={service.id} style={{ display: 'flex', alignItems: 'center', gap: '0.8rem', cursor: 'pointer', background: '#1a1a1a', borderRadius: '8px', padding: '0.8rem 1rem', border: '1px solid #2a2a2a' }}>
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
          )}
          <div style={{ display: 'flex', gap: '0.7rem' }}>
            <button onClick={() => setStep(6)} style={btnPrimary}>Далее →</button>
            <button onClick={() => setStep(4)} style={{ ...btnPrimary, background: 'transparent', border: '1px solid #444', color: '#aaa' }}>
              ← Назад
            </button>
          </div>
        </div>
      )}

      {/* Шаг 6: Сводка и подтверждение заказа. */}
      {step === 6 && selectedSession && selectedSeat && (
        <div>
          <h3 style={{ marginBottom: '1.5rem' }}>Шаг 6: Подтверждение</h3>
          {/* Таблица сводки: клиент / фильм / сеанс / место / доп.услуги / итого. */}
          <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.5rem', border: '1px solid #2a2a2a', marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#aaa' }}>Клиент</span>
                <span>#{clientId}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#aaa' }}>Фильм</span>
                <span>{selectedMovie?.title}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#aaa' }}>Сеанс</span>
                <span>{new Date(selectedSession.startTime).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#aaa' }}>Место</span>
                <span>Ряд {selectedSeat.row}, место {selectedSeat.seat}</span>
              </div>
              {/* Доп.услуги — только если выбраны. */}
              {selectedServices.length > 0 && (
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ color: '#aaa' }}>Доп. услуги</span>
                  <span>+{servicesPrice} ₽</span>
                </div>
              )}
              <div style={{ height: '1px', background: '#333', margin: '0.3rem 0' }} />
              {/* Итоговая строка — крупный шрифт. */}
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '1.1rem', fontWeight: '700' }}>
                <span>Итого</span>
                <span style={{ color: '#e50914' }}>{totalPrice} ₽</span>
              </div>
            </div>
          </div>

          {error && (
            <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '6px', padding: '0.7rem', marginBottom: '1rem', color: '#ff6b6b', fontSize: '0.9rem' }}>
              {error}
            </div>
          )}

          <div style={{ display: 'flex', gap: '0.7rem' }}>
            <button onClick={confirmOrder} disabled={loading} style={{ ...btnPrimary, opacity: loading ? 0.6 : 1 }}>
              {loading ? 'Оформление...' : `✓ Оформить заказ на ${totalPrice} ₽`}
            </button>
            <button onClick={() => setStep(5)} style={{ ...btnPrimary, background: 'transparent', border: '1px solid #444', color: '#aaa' }}>
              ← Назад
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// ==================== SELL FOOD TAB ====================
// SellFoodTab — продажа еды продавцом для конкретного клиента.
function SellFoodTab() {
  const [clientId, setClientId] = useState('');
  const [foodItems, setFoodItems] = useState<FoodItem[]>([]);
  // cart: маппинг foodItemId → quantity.
  const [cart, setCart] = useState<Record<number, number>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState<{ orderId: number; totalPrice: number } | null>(null);

  useEffect(() => {
    fetchFood();
  }, []);

  const fetchFood = async () => {
    setLoading(true);
    try {
      const res = await api.get<FoodItem[]>('/food-menu');
      setFoodItems(res.data || []);
    } catch { setError('Ошибка загрузки меню'); } finally { setLoading(false); }
  };

  // setQty — устанавливает количество товара напрямую (а не +/-1 как в FoodMenuPage).
  const setQty = (id: number, qty: number) => {
    if (qty <= 0) {
      // Удаляем ключ при qty <= 0 (аналогично removeFromCart в FoodMenuPage).
      setCart((c) => { const n = { ...c }; delete n[id]; return n; });
    } else {
      setCart((c) => ({ ...c, [id]: qty }));
    }
  };

  const cartItems = foodItems.filter((f) => cart[f.id]);
  const totalPrice = cartItems.reduce((sum, f) => sum + f.price * (cart[f.id] || 0), 0);

  // handleOrder — создаёт заказ еды от имени продавца.
  const handleOrder = async () => {
    if (!clientId.trim() || isNaN(parseInt(clientId))) {
      setError('Введите корректный ID клиента');
      return;
    }
    if (cartItems.length === 0) {
      setError('Выберите хотя бы один товар');
      return;
    }
    setError('');
    setLoading(true);
    try {
      // POST /api/orders/food — seller endpoint (с clientId в теле).
      // Отличается от /orders/food/client — продавец указывает clientId явно.
      const items = cartItems.map((f) => ({ foodItemId: f.id, quantity: cart[f.id] || 1 }));
      const res = await api.post('/orders/food', { clientId: parseInt(clientId), items });
      setSuccess({ orderId: res.data.id, totalPrice: res.data.totalPrice });
    } catch (e: any) {
      setError(e.response?.data?.message || 'Ошибка создания заказа');
    } finally {
      setLoading(false);
    }
  };

  // Уникальные категории из загруженных позиций.
  const categories = [...new Set(foodItems.map((f) => f.category).filter(Boolean))];

  // Экран успеха.
  if (success) {
    return (
      <div style={{ textAlign: 'center', padding: '3rem' }}>
        <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>✅</div>
        <h2 style={{ color: '#4caf50', marginBottom: '0.5rem' }}>Заказ оформлен!</h2>
        <div style={{ background: '#0d2d0d', border: '1px solid #4caf50', borderRadius: '10px', padding: '1.5rem', maxWidth: '350px', margin: '0 auto 2rem' }}>
          <div style={{ color: '#aaa', marginBottom: '0.4rem' }}>Заказ #{success.orderId}</div>
          <div style={{ color: '#e50914', fontSize: '1.5rem', fontWeight: '700' }}>{success.totalPrice} ₽</div>
        </div>
        {/* Сброс: очищаем корзину, clientId, success. */}
        <button onClick={() => { setSuccess(null); setCart({}); setClientId(''); }} style={btnPrimary}>
          Оформить ещё
        </button>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
      {/* Левая колонка: поле clientId + меню. */}
      <div style={{ flex: 1, minWidth: '300px' }}>
        <div style={{ marginBottom: '1rem' }}>
          <input
            style={{ ...inputStyle, marginBottom: 0 }}
            placeholder="ID клиента"
            type="number"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
          />
        </div>

        <h3 style={{ marginBottom: '1rem', color: '#aaa', fontSize: '0.9rem', textTransform: 'uppercase', letterSpacing: '1px' }}>
          Меню
        </h3>

        {loading && <div style={{ color: '#aaa', padding: '1rem' }}>⏳ Загрузка...</div>}

        {/* Группировка по категориям; если категорий нет — рендерим все товары без заголовка. */}
        {(categories.length > 0 ? categories : ['']).map((cat) => (
          <div key={cat} style={{ marginBottom: '1.5rem' }}>
            {cat && <div style={{ color: '#666', fontSize: '0.8rem', marginBottom: '0.5rem', textTransform: 'uppercase', letterSpacing: '1px' }}>{cat}</div>}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              {/* cat ? items.filter(...) : items — если '' (пустая категория), показываем все. */}
              {(cat ? foodItems.filter((f) => f.category === cat) : foodItems).map((item) => (
                <div key={item.id} style={{
                  background: '#1a1a1a',
                  borderRadius: '8px',
                  padding: '0.8rem 1rem',
                  // Красная рамка если товар в корзине.
                  border: `1px solid ${cart[item.id] ? '#e50914' : '#2a2a2a'}`,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}>
                  <div>
                    <div style={{ fontWeight: '500' }}>{item.name}</div>
                    <div style={{ color: '#e50914', fontSize: '0.9rem', fontWeight: '600' }}>{item.price} ₽</div>
                  </div>
                  {/* Счётчик количества: кнопки − и + с текущим значением. */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <button
                      onClick={() => setQty(item.id, (cart[item.id] || 0) - 1)}
                      style={{ background: '#333', border: 'none', color: '#fff', width: '28px', height: '28px', borderRadius: '4px', fontSize: '1rem', cursor: 'pointer' }}
                    >
                      −
                    </button>
                    <span style={{ minWidth: '20px', textAlign: 'center', fontWeight: '600' }}>
                      {cart[item.id] || 0}
                    </span>
                    <button
                      onClick={() => setQty(item.id, (cart[item.id] || 0) + 1)}
                      style={{ background: '#e50914', border: 'none', color: '#fff', width: '28px', height: '28px', borderRadius: '4px', fontSize: '1rem', cursor: 'pointer' }}
                    >
                      +
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* Правая колонка: корзина (sticky сайдбар). */}
      <div style={{ flex: '0 0 280px', minWidth: '240px' }}>
        <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.2rem', border: '1px solid #2a2a2a', position: 'sticky', top: '80px' }}>
          <h3 style={{ marginBottom: '1rem', fontSize: '1rem' }}>🛒 Корзина</h3>

          {cartItems.length === 0 ? (
            <div style={{ color: '#555', textAlign: 'center', padding: '1.5rem 0' }}>Корзина пуста</div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginBottom: '1rem' }}>
              {cartItems.map((item) => (
                <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.9rem' }}>
                  <span style={{ color: '#ccc' }}>{item.name} × {cart[item.id]}</span>
                  <span style={{ color: '#fff' }}>{item.price * (cart[item.id] || 0)} ₽</span>
                </div>
              ))}
              <div style={{ height: '1px', background: '#333', margin: '0.5rem 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: '700', fontSize: '1.05rem' }}>
                <span>Итого</span>
                <span style={{ color: '#e50914' }}>{totalPrice} ₽</span>
              </div>
            </div>
          )}

          {error && <div style={{ color: '#ff6b6b', fontSize: '0.85rem', marginBottom: '0.7rem' }}>{error}</div>}

          <button
            onClick={handleOrder}
            disabled={loading || cartItems.length === 0}
            style={{
              ...btnPrimary,
              width: '100%',
              opacity: (loading || cartItems.length === 0) ? 0.5 : 1,
              cursor: (loading || cartItems.length === 0) ? 'not-allowed' : 'pointer',
            }}
          >
            {loading ? 'Оформление...' : 'Оформить заказ'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ==================== MY ORDERS TAB ====================
// MyOrdersTab — история заказов, оформленных этим продавцом.
function MyOrdersTab() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchOrders = async () => {
      setLoading(true);
      try {
        // GET /api/orders/my — возвращает заказы где sellerId = текущий пользователь.
        const res = await api.get<Order[]>('/orders/my');
        setOrders(res.data || []);
      } catch { setError('Ошибка загрузки'); } finally { setLoading(false); }
    };
    fetchOrders();
  }, []);

  const statusColors: Record<string, string> = {
    PENDING:   '#f5a623',
    PAID:      '#4caf50',
    CANCELLED: '#e50914',
  };

  const statusLabels: Record<string, string> = {
    PENDING:   'Ожидает',
    PAID:      'Оплачен',
    CANCELLED: 'Отменён',
  };

  return (
    <div>
      <h2 style={{ fontSize: '1.2rem', fontWeight: '600', marginBottom: '1.5rem' }}>
        Заказы ({orders.length})
      </h2>

      {loading && <div style={{ color: '#aaa', padding: '2rem', textAlign: 'center' }}>⏳ Загрузка...</div>}
      {error && <div style={{ color: '#ff6b6b', marginBottom: '1rem' }}>{error}</div>}

      {!loading && orders.length === 0 && (
        <div style={{ textAlign: 'center', color: '#666', padding: '3rem' }}>Нет заказов</div>
      )}

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
          {/* Заголовок таблицы — рендерится только если есть данные. */}
          {orders.length > 0 && (
            <thead>
              <tr style={{ background: '#1a1a1a', color: '#aaa', textAlign: 'left' }}>
                {['ID', 'Клиент', 'Тип', 'Статус', 'Сумма', 'Дата'].map((h) => (
                  <th key={h} style={{ padding: '0.8rem', borderBottom: '1px solid #2a2a2a' }}>{h}</th>
                ))}
              </tr>
            </thead>
          )}
          <tbody>
            {orders.map((order) => (
              <tr key={order.id} style={{ borderBottom: '1px solid #1a1a1a' }}>
                <td style={{ padding: '0.8rem', color: '#666' }}>#{order.id}</td>
                {/* order.userId — ID клиента заказа. */}
                <td style={{ padding: '0.8rem' }}>#{order.userId}</td>
                <td style={{ padding: '0.8rem', color: '#aaa' }}>{order.orderType}</td>
                <td style={{ padding: '0.8rem' }}>
                  <span style={{ color: statusColors[order.status] || '#aaa', fontWeight: '600', fontSize: '0.85rem' }}>
                    {statusLabels[order.status] || order.status}
                  </span>
                </td>
                <td style={{ padding: '0.8rem', color: '#e50914', fontWeight: '600' }}>{order.totalPrice} ₽</td>
                <td style={{ padding: '0.8rem', color: '#666', fontSize: '0.85rem', whiteSpace: 'nowrap' }}>
                  {new Date(order.createdAt).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ==================== MAIN SELLER PAGE ====================
// SellerPage — рабочее место продавца: три вкладки.
export default function SellerPage() {
  const [activeTab, setActiveTab] = useState<Tab>('ticket');

  return (
    <div>
      <h1 style={{ fontSize: '1.8rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Рабочее место продавца</h1>
      <p style={{ color: '#aaa', marginBottom: '2rem' }}>Оформление заказов для клиентов</p>

      {/* Панель вкладок — оранжевый акцент (отличие от AdminPage с красным). */}
      <div style={{ display: 'flex', gap: '0', marginBottom: '2rem', borderBottom: '2px solid #2a2a2a', overflowX: 'auto' }}>
        {TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            style={{
              background: 'transparent',
              border: 'none',
              // Продавец использует оранжевый акцент (#f5a623) вместо красного.
              color: activeTab === tab.key ? '#f5a623' : '#666',
              borderBottom: activeTab === tab.key ? '2px solid #f5a623' : '2px solid transparent',
              marginBottom: '-2px',
              padding: '0.7rem 1.5rem',
              fontSize: '0.95rem',
              fontWeight: activeTab === tab.key ? '700' : '400',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Рендер активного таба. */}
      <div>
        {activeTab === 'ticket' && <SellTicketTab />}
        {activeTab === 'food'   && <SellFoodTab />}
        {activeTab === 'orders' && <MyOrdersTab />}
      </div>
    </div>
  );
}
