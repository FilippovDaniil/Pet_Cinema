import { useState, useEffect } from 'react';
import api from '../api/axios';
import { Movie, Hall, Session, FoodItem, SupportTicket, SupportMessage, Genre, ExtraService } from '../types';
import { useAuth } from '../context/AuthContext';

type Tab = 'movies' | 'halls' | 'sessions' | 'menu' | 'support';

const TABS: { key: Tab; label: string }[] = [
  { key: 'movies', label: 'Фильмы' },
  { key: 'halls', label: 'Залы' },
  { key: 'sessions', label: 'Сеансы' },
  { key: 'menu', label: 'Меню' },
  { key: 'support', label: 'Поддержка' },
];

const inputStyle: React.CSSProperties = {
  width: '100%',
  background: '#111',
  border: '1.5px solid #333',
  borderRadius: '6px',
  color: '#fff',
  padding: '0.6rem 0.8rem',
  fontSize: '0.9rem',
  marginBottom: '0.7rem',
};

const btnPrimary: React.CSSProperties = {
  background: '#e50914',
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  padding: '0.6rem 1.2rem',
  fontWeight: '600',
  fontSize: '0.9rem',
  cursor: 'pointer',
};

const btnSecondary: React.CSSProperties = {
  background: 'transparent',
  color: '#aaa',
  border: '1px solid #444',
  borderRadius: '6px',
  padding: '0.5rem 1rem',
  fontWeight: '600',
  fontSize: '0.85rem',
  cursor: 'pointer',
};

const btnDanger: React.CSSProperties = {
  background: 'transparent',
  color: '#e50914',
  border: '1px solid #e50914',
  borderRadius: '6px',
  padding: '0.4rem 0.8rem',
  fontSize: '0.8rem',
  cursor: 'pointer',
};

// -------------------- MOVIES TAB --------------------
function MoviesTab() {
  const [movies, setMovies] = useState<Movie[]>([]);
  const [genres, setGenres] = useState<Genre[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editMovie, setEditMovie] = useState<Movie | null>(null);
  const [form, setForm] = useState({
    title: '',
    description: '',
    posterUrl: '',
    durationMinutes: '',
    type: 'TWO_D',
    genreIds: [] as number[],
  });
  const [error, setError] = useState('');

  useEffect(() => {
    fetchMovies();
    api.get<Genre[]>('/genres').then((r) => setGenres(r.data)).catch(() => {});
  }, []);

  const fetchMovies = async () => {
    setLoading(true);
    try {
      const res = await api.get('/movies', { params: { size: 100 } });
      setMovies(res.data.content || res.data);
    } catch { setError('Ошибка загрузки'); } finally { setLoading(false); }
  };

  const openEdit = (movie: Movie) => {
    setEditMovie(movie);
    setForm({
      title: movie.title,
      description: movie.description,
      posterUrl: movie.posterUrl,
      durationMinutes: String(movie.durationMinutes),
      type: movie.type,
      genreIds: [],
    });
    setShowForm(true);
  };

  const openCreate = () => {
    setEditMovie(null);
    setForm({ title: '', description: '', posterUrl: '', durationMinutes: '', type: 'TWO_D', genreIds: [] });
    setShowForm(true);
  };

  const handleSubmit = async () => {
    setError('');
    const payload = {
      title: form.title,
      description: form.description,
      posterUrl: form.posterUrl,
      durationMinutes: parseInt(form.durationMinutes) || 0,
      type: form.type,
      genreIds: form.genreIds,
    };
    try {
      if (editMovie) {
        await api.put(`/movies/${editMovie.id}`, payload);
      } else {
        await api.post('/movies', payload);
      }
      setShowForm(false);
      fetchMovies();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Ошибка при сохранении');
    }
  };

  const deleteMovie = async (id: number) => {
    if (!confirm('Удалить фильм?')) return;
    try { await api.delete(`/movies/${id}`); fetchMovies(); } catch { alert('Ошибка удаления'); }
  };

  const toggleGenre = (id: number) => {
    setForm((f) => ({
      ...f,
      genreIds: f.genreIds.includes(id) ? f.genreIds.filter((g) => g !== id) : [...f.genreIds, id],
    }));
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h2 style={{ fontSize: '1.2rem', fontWeight: '600' }}>Управление фильмами</h2>
        <button onClick={openCreate} style={btnPrimary}>+ Добавить фильм</button>
      </div>

      {showForm && (
        <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.5rem', marginBottom: '1.5rem', border: '1px solid #333' }}>
          <h3 style={{ marginBottom: '1rem', fontSize: '1rem' }}>{editMovie ? 'Редактировать фильм' : 'Новый фильм'}</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 1rem' }}>
            <div>
              <input style={inputStyle} placeholder="Название" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
              <input style={inputStyle} placeholder="URL постера" value={form.posterUrl} onChange={(e) => setForm({ ...form, posterUrl: e.target.value })} />
              <input style={inputStyle} placeholder="Длительность (мин)" type="number" value={form.durationMinutes} onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })} />
            </div>
            <div>
              <select style={{ ...inputStyle, cursor: 'pointer' }} value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
                <option value="TWO_D">2D</option>
                <option value="THREE_D">3D</option>
                <option value="FIVE_D">5D</option>
              </select>
              <div style={{ color: '#aaa', fontSize: '0.85rem', marginBottom: '0.5rem' }}>Жанры:</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.4rem', marginBottom: '0.7rem' }}>
                {genres.map((g) => (
                  <button
                    key={g.id}
                    type="button"
                    onClick={() => toggleGenre(g.id)}
                    style={{
                      padding: '3px 10px',
                      borderRadius: '4px',
                      border: `1px solid ${form.genreIds.includes(g.id) ? '#e50914' : '#444'}`,
                      background: form.genreIds.includes(g.id) ? '#e50914' : 'transparent',
                      color: '#fff',
                      fontSize: '0.8rem',
                      cursor: 'pointer',
                    }}
                  >
                    {g.name}
                  </button>
                ))}
              </div>
            </div>
          </div>
          <textarea
            style={{ ...inputStyle, height: '80px', resize: 'vertical' }}
            placeholder="Описание"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          {error && <div style={{ color: '#ff6b6b', fontSize: '0.85rem', marginBottom: '0.7rem' }}>{error}</div>}
          <div style={{ display: 'flex', gap: '0.7rem' }}>
            <button onClick={handleSubmit} style={btnPrimary}>Сохранить</button>
            <button onClick={() => setShowForm(false)} style={btnSecondary}>Отмена</button>
          </div>
        </div>
      )}

      {loading && <div style={{ color: '#aaa', padding: '2rem', textAlign: 'center' }}>⏳ Загрузка...</div>}
      {error && !showForm && <div style={{ color: '#ff6b6b', marginBottom: '1rem' }}>{error}</div>}

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ background: '#1a1a1a', color: '#aaa', textAlign: 'left' }}>
              <th style={{ padding: '0.8rem', borderBottom: '1px solid #2a2a2a' }}>ID</th>
              <th style={{ padding: '0.8rem', borderBottom: '1px solid #2a2a2a' }}>Название</th>
              <th style={{ padding: '0.8rem', borderBottom: '1px solid #2a2a2a' }}>Тип</th>
              <th style={{ padding: '0.8rem', borderBottom: '1px solid #2a2a2a' }}>Длит.</th>
              <th style={{ padding: '0.8rem', borderBottom: '1px solid #2a2a2a' }}>Рейтинг</th>
              <th style={{ padding: '0.8rem', borderBottom: '1px solid #2a2a2a' }}>Действия</th>
            </tr>
          </thead>
          <tbody>
            {movies.map((movie) => (
              <tr key={movie.id} style={{ borderBottom: '1px solid #1a1a1a' }}>
                <td style={{ padding: '0.8rem', color: '#666' }}>#{movie.id}</td>
                <td style={{ padding: '0.8rem', fontWeight: '500' }}>{movie.title}</td>
                <td style={{ padding: '0.8rem' }}>
                  <span style={{ background: '#222', borderRadius: '4px', padding: '2px 8px', fontSize: '0.8rem', color: '#aaa' }}>
                    {movie.type}
                  </span>
                </td>
                <td style={{ padding: '0.8rem', color: '#aaa' }}>{movie.durationMinutes} мин</td>
                <td style={{ padding: '0.8rem', color: '#f5a623' }}>
                  {movie.averageRating ? movie.averageRating.toFixed(1) : '—'}
                </td>
                <td style={{ padding: '0.8rem' }}>
                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button onClick={() => openEdit(movie)} style={btnSecondary}>Изменить</button>
                    <button onClick={() => deleteMovie(movie.id)} style={btnDanger}>Удалить</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// -------------------- HALLS TAB --------------------
function HallsTab() {
  const [halls, setHalls] = useState<Hall[]>([]);
  const [extraServices, setExtraServices] = useState<Record<number, ExtraService[]>>({});
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [selectedHall, setSelectedHall] = useState<Hall | null>(null);
  const [form, setForm] = useState({ name: '', type: 'NORMAL', rowsCount: '', seatsPerRow: '', description: '' });
  const [serviceForm, setServiceForm] = useState({ name: '', price: '' });
  const [error, setError] = useState('');

  useEffect(() => { fetchHalls(); }, []);

  const fetchHalls = async () => {
    setLoading(true);
    try {
      const res = await api.get<Hall[]>('/halls');
      setHalls(res.data || []);
      const map: Record<number, ExtraService[]> = {};
      await Promise.all((res.data || []).map(async (h) => {
        try {
          const r = await api.get<ExtraService[]>(`/halls/${h.id}/extra-services`);
          map[h.id] = r.data;
        } catch { map[h.id] = []; }
      }));
      setExtraServices(map);
    } catch { setError('Ошибка загрузки'); } finally { setLoading(false); }
  };

  const createHall = async () => {
    setError('');
    try {
      await api.post('/halls', {
        name: form.name,
        type: form.type,
        rowsCount: parseInt(form.rowsCount) || 0,
        seatsPerRow: parseInt(form.seatsPerRow) || 0,
        description: form.description,
      });
      setShowForm(false);
      setForm({ name: '', type: 'NORMAL', rowsCount: '', seatsPerRow: '', description: '' });
      fetchHalls();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Ошибка создания зала');
    }
  };

  const deleteHall = async (id: number) => {
    if (!confirm('Удалить зал?')) return;
    try { await api.delete(`/halls/${id}`); fetchHalls(); } catch { alert('Ошибка'); }
  };

  const addService = async () => {
    if (!selectedHall || !serviceForm.name) return;
    try {
      await api.post(`/halls/${selectedHall.id}/extra-services`, {
        name: serviceForm.name,
        price: parseFloat(serviceForm.price) || 0,
      });
      setServiceForm({ name: '', price: '' });
      const r = await api.get<ExtraService[]>(`/halls/${selectedHall.id}/extra-services`);
      setExtraServices((prev) => ({ ...prev, [selectedHall.id]: r.data }));
    } catch { alert('Ошибка добавления услуги'); }
  };

  const deleteService = async (hallId: number, serviceId: number) => {
    try {
      await api.delete(`/halls/${hallId}/extra-services/${serviceId}`);
      const r = await api.get<ExtraService[]>(`/halls/${hallId}/extra-services`);
      setExtraServices((prev) => ({ ...prev, [hallId]: r.data }));
    } catch { alert('Ошибка удаления'); }
  };

  const hallTypeBadge: Record<string, { label: string; color: string }> = {
    NORMAL: { label: 'Обычный', color: '#1a73e8' },
    VIP: { label: 'VIP', color: '#f5a623' },
    THREE_D: { label: '3D', color: '#0d7a4e' },
    FIVE_D: { label: '5D', color: '#7b1fa2' },
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h2 style={{ fontSize: '1.2rem', fontWeight: '600' }}>Управление залами</h2>
        <button onClick={() => setShowForm(!showForm)} style={btnPrimary}>+ Добавить зал</button>
      </div>

      {showForm && (
        <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.5rem', marginBottom: '1.5rem', border: '1px solid #333' }}>
          <h3 style={{ marginBottom: '1rem', fontSize: '1rem' }}>Новый зал</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 1rem' }}>
            <div>
              <input style={inputStyle} placeholder="Название зала" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              <select style={{ ...inputStyle, cursor: 'pointer' }} value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
                <option value="NORMAL">Обычный</option>
                <option value="VIP">VIP</option>
                <option value="THREE_D">3D</option>
                <option value="FIVE_D">5D</option>
              </select>
            </div>
            <div>
              <input style={inputStyle} placeholder="Количество рядов" type="number" value={form.rowsCount} onChange={(e) => setForm({ ...form, rowsCount: e.target.value })} />
              <input style={inputStyle} placeholder="Мест в ряду" type="number" value={form.seatsPerRow} onChange={(e) => setForm({ ...form, seatsPerRow: e.target.value })} />
            </div>
          </div>
          <input style={inputStyle} placeholder="Описание" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          {error && <div style={{ color: '#ff6b6b', fontSize: '0.85rem', marginBottom: '0.7rem' }}>{error}</div>}
          <div style={{ display: 'flex', gap: '0.7rem' }}>
            <button onClick={createHall} style={btnPrimary}>Создать</button>
            <button onClick={() => setShowForm(false)} style={btnSecondary}>Отмена</button>
          </div>
        </div>
      )}

      {loading && <div style={{ color: '#aaa', padding: '2rem', textAlign: 'center' }}>⏳ Загрузка...</div>}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '1rem' }}>
        {halls.map((hall) => {
          const badge = hallTypeBadge[hall.type] || { label: hall.type, color: '#444' };
          const services = extraServices[hall.id] || [];
          return (
            <div key={hall.id} style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.2rem', border: '1px solid #2a2a2a' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.8rem' }}>
                <div>
                  <div style={{ fontWeight: '600', marginBottom: '0.3rem' }}>{hall.name}</div>
                  <span style={{ background: badge.color, color: '#fff', fontSize: '0.75rem', fontWeight: '700', padding: '2px 8px', borderRadius: '4px' }}>
                    {badge.label}
                  </span>
                </div>
                <button onClick={() => deleteHall(hall.id)} style={btnDanger}>Удалить</button>
              </div>
              <div style={{ color: '#aaa', fontSize: '0.85rem', marginBottom: '1rem' }}>
                {hall.rowsCount} рядов × {hall.seatsPerRow} мест = {hall.rowsCount * hall.seatsPerRow} мест
              </div>

              {/* Extra Services */}
              <div style={{ borderTop: '1px solid #2a2a2a', paddingTop: '0.8rem' }}>
                <div style={{ color: '#888', fontSize: '0.8rem', marginBottom: '0.5rem' }}>Дополнительные услуги:</div>
                {services.map((s) => (
                  <div key={s.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
                    <span style={{ color: '#ccc', fontSize: '0.85rem' }}>{s.name} — {s.price}₽</span>
                    <button onClick={() => deleteService(hall.id, s.id)} style={{ ...btnDanger, padding: '2px 6px', fontSize: '0.75rem' }}>✕</button>
                  </div>
                ))}

                {selectedHall?.id === hall.id ? (
                  <div style={{ marginTop: '0.5rem', display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                    <input
                      style={{ ...inputStyle, marginBottom: 0, flex: '1 1 120px' }}
                      placeholder="Название услуги"
                      value={serviceForm.name}
                      onChange={(e) => setServiceForm({ ...serviceForm, name: e.target.value })}
                    />
                    <input
                      style={{ ...inputStyle, marginBottom: 0, width: '80px' }}
                      placeholder="Цена"
                      type="number"
                      value={serviceForm.price}
                      onChange={(e) => setServiceForm({ ...serviceForm, price: e.target.value })}
                    />
                    <button onClick={addService} style={{ ...btnPrimary, padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}>+</button>
                    <button onClick={() => setSelectedHall(null)} style={{ ...btnSecondary, padding: '0.4rem 0.6rem', fontSize: '0.8rem' }}>✕</button>
                  </div>
                ) : (
                  <button onClick={() => setSelectedHall(hall)} style={{ ...btnSecondary, marginTop: '0.4rem', padding: '0.3rem 0.7rem', fontSize: '0.8rem' }}>
                    + Добавить услугу
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// -------------------- SESSIONS TAB --------------------
function SessionsTab() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [movies, setMovies] = useState<Movie[]>([]);
  const [halls, setHalls] = useState<Hall[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ movieId: '', hallId: '', startTime: '', endTime: '', basePrice: '', active: true });
  const [error, setError] = useState('');

  useEffect(() => {
    fetchAll();
  }, []);

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [sessionsRes, moviesRes, hallsRes] = await Promise.all([
        api.get('/sessions', { params: { size: 100 } }),
        api.get('/movies', { params: { size: 100 } }),
        api.get<Hall[]>('/halls'),
      ]);
      setSessions(sessionsRes.data.content || sessionsRes.data || []);
      setMovies(moviesRes.data.content || moviesRes.data || []);
      setHalls(hallsRes.data || []);
    } catch { setError('Ошибка загрузки'); } finally { setLoading(false); }
  };

  const createSession = async () => {
    setError('');
    try {
      await api.post('/sessions', {
        movieId: parseInt(form.movieId),
        hallId: parseInt(form.hallId),
        startTime: form.startTime,
        endTime: form.endTime,
        basePrice: parseFloat(form.basePrice),
        active: form.active,
      });
      setShowForm(false);
      setForm({ movieId: '', hallId: '', startTime: '', endTime: '', basePrice: '', active: true });
      fetchAll();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Ошибка создания сеанса');
    }
  };

  const deleteSession = async (id: number) => {
    if (!confirm('Удалить сеанс?')) return;
    try { await api.delete(`/sessions/${id}`); fetchAll(); } catch { alert('Ошибка'); }
  };

  const movieName = (id: number) => movies.find((m) => m.id === id)?.title || `#${id}`;
  const hallName = (id: number) => halls.find((h) => h.id === id)?.name || `#${id}`;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h2 style={{ fontSize: '1.2rem', fontWeight: '600' }}>Управление сеансами</h2>
        <button onClick={() => setShowForm(!showForm)} style={btnPrimary}>+ Добавить сеанс</button>
      </div>

      {showForm && (
        <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.5rem', marginBottom: '1.5rem', border: '1px solid #333' }}>
          <h3 style={{ marginBottom: '1rem', fontSize: '1rem' }}>Новый сеанс</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 1rem' }}>
            <div>
              <select style={{ ...inputStyle, cursor: 'pointer' }} value={form.movieId} onChange={(e) => setForm({ ...form, movieId: e.target.value })}>
                <option value="">Выберите фильм</option>
                {movies.map((m) => <option key={m.id} value={m.id}>{m.title}</option>)}
              </select>
              <input style={inputStyle} type="datetime-local" value={form.startTime} onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
              <input style={inputStyle} placeholder="Цена (руб.)" type="number" value={form.basePrice} onChange={(e) => setForm({ ...form, basePrice: e.target.value })} />
            </div>
            <div>
              <select style={{ ...inputStyle, cursor: 'pointer' }} value={form.hallId} onChange={(e) => setForm({ ...form, hallId: e.target.value })}>
                <option value="">Выберите зал</option>
                {halls.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
              </select>
              <input style={inputStyle} type="datetime-local" value={form.endTime} onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#aaa', fontSize: '0.9rem', marginTop: '0.3rem' }}>
                <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
                Активный сеанс
              </label>
            </div>
          </div>
          {error && <div style={{ color: '#ff6b6b', fontSize: '0.85rem', margin: '0.7rem 0' }}>{error}</div>}
          <div style={{ display: 'flex', gap: '0.7rem', marginTop: '0.7rem' }}>
            <button onClick={createSession} style={btnPrimary}>Создать</button>
            <button onClick={() => setShowForm(false)} style={btnSecondary}>Отмена</button>
          </div>
        </div>
      )}

      {loading && <div style={{ color: '#aaa', padding: '2rem', textAlign: 'center' }}>⏳ Загрузка...</div>}

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ background: '#1a1a1a', color: '#aaa', textAlign: 'left' }}>
              {['ID', 'Фильм', 'Зал', 'Начало', 'Цена', 'Статус', 'Действия'].map((h) => (
                <th key={h} style={{ padding: '0.8rem', borderBottom: '1px solid #2a2a2a' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sessions.map((s) => (
              <tr key={s.id} style={{ borderBottom: '1px solid #1a1a1a' }}>
                <td style={{ padding: '0.8rem', color: '#666' }}>#{s.id}</td>
                <td style={{ padding: '0.8rem' }}>{movieName(s.movieId)}</td>
                <td style={{ padding: '0.8rem', color: '#aaa' }}>{hallName(s.hallId)}</td>
                <td style={{ padding: '0.8rem', color: '#aaa', whiteSpace: 'nowrap' }}>
                  {new Date(s.startTime).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                </td>
                <td style={{ padding: '0.8rem', color: '#e50914', fontWeight: '600' }}>{s.basePrice} ₽</td>
                <td style={{ padding: '0.8rem' }}>
                  <span style={{ color: s.active ? '#4caf50' : '#666', fontSize: '0.85rem' }}>
                    {s.active ? '● Активен' : '○ Неактивен'}
                  </span>
                </td>
                <td style={{ padding: '0.8rem' }}>
                  <button onClick={() => deleteSession(s.id)} style={btnDanger}>Удалить</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// -------------------- MENU TAB --------------------
function MenuTab() {
  const [items, setItems] = useState<FoodItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: '', price: '', category: '' });
  const [error, setError] = useState('');

  useEffect(() => { fetchItems(); }, []);

  const fetchItems = async () => {
    setLoading(true);
    try {
      const res = await api.get<FoodItem[]>('/food-menu');
      setItems(res.data || []);
    } catch { setError('Ошибка загрузки'); } finally { setLoading(false); }
  };

  const createItem = async () => {
    setError('');
    try {
      await api.post('/food-menu', {
        name: form.name,
        price: parseFloat(form.price) || 0,
        category: form.category,
      });
      setShowForm(false);
      setForm({ name: '', price: '', category: '' });
      fetchItems();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Ошибка добавления');
    }
  };

  const deleteItem = async (id: number) => {
    if (!confirm('Удалить позицию меню?')) return;
    try { await api.delete(`/food-menu/${id}`); fetchItems(); } catch { alert('Ошибка'); }
  };

  const categories = [...new Set(items.map((i) => i.category).filter(Boolean))];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h2 style={{ fontSize: '1.2rem', fontWeight: '600' }}>Меню (Еда и напитки)</h2>
        <button onClick={() => setShowForm(!showForm)} style={btnPrimary}>+ Добавить позицию</button>
      </div>

      {showForm && (
        <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.5rem', marginBottom: '1.5rem', border: '1px solid #333' }}>
          <h3 style={{ marginBottom: '1rem', fontSize: '1rem' }}>Новая позиция</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0 1rem' }}>
            <input style={inputStyle} placeholder="Название" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <input style={inputStyle} placeholder="Цена (₽)" type="number" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
            <input style={inputStyle} placeholder="Категория" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} />
          </div>
          {error && <div style={{ color: '#ff6b6b', fontSize: '0.85rem', marginBottom: '0.7rem' }}>{error}</div>}
          <div style={{ display: 'flex', gap: '0.7rem' }}>
            <button onClick={createItem} style={btnPrimary}>Добавить</button>
            <button onClick={() => setShowForm(false)} style={btnSecondary}>Отмена</button>
          </div>
        </div>
      )}

      {loading && <div style={{ color: '#aaa', padding: '2rem', textAlign: 'center' }}>⏳ Загрузка...</div>}

      {categories.length === 0 ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '1rem' }}>
          {items.map((item) => (
            <div key={item.id} style={{ background: '#1a1a1a', borderRadius: '8px', padding: '1rem', border: '1px solid #2a2a2a' }}>
              <div style={{ fontWeight: '600', marginBottom: '0.3rem' }}>{item.name}</div>
              <div style={{ color: '#e50914', fontWeight: '700', marginBottom: '0.5rem' }}>{item.price} ₽</div>
              <div style={{ color: '#666', fontSize: '0.8rem', marginBottom: '0.7rem' }}>{item.category}</div>
              <button onClick={() => deleteItem(item.id)} style={{ ...btnDanger, width: '100%' }}>Удалить</button>
            </div>
          ))}
        </div>
      ) : (
        categories.map((cat) => (
          <div key={cat} style={{ marginBottom: '1.5rem' }}>
            <h3 style={{ color: '#aaa', fontSize: '0.9rem', marginBottom: '0.8rem', textTransform: 'uppercase', letterSpacing: '1px' }}>{cat}</h3>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '0.8rem' }}>
              {items.filter((i) => i.category === cat).map((item) => (
                <div key={item.id} style={{ background: '#1a1a1a', borderRadius: '8px', padding: '1rem', border: '1px solid #2a2a2a' }}>
                  <div style={{ fontWeight: '600', marginBottom: '0.3rem' }}>{item.name}</div>
                  <div style={{ color: '#e50914', fontWeight: '700', marginBottom: '0.7rem' }}>{item.price} ₽</div>
                  <button onClick={() => deleteItem(item.id)} style={{ ...btnDanger, width: '100%' }}>Удалить</button>
                </div>
              ))}
            </div>
          </div>
        ))
      )}
    </div>
  );
}

// -------------------- SUPPORT TAB --------------------
function SupportAdminTab() {
  const { user } = useAuth();
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<SupportTicket | null>(null);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [newMessage, setNewMessage] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchTickets();
  }, []);

  useEffect(() => {
    if (selectedTicket) {
      fetchMessages(selectedTicket.id);
      const interval = setInterval(() => fetchMessages(selectedTicket.id), 5000);
      return () => clearInterval(interval);
    }
  }, [selectedTicket]);

  const fetchTickets = async () => {
    setLoading(true);
    try {
      const res = await api.get<SupportTicket[]>('/support/tickets');
      setTickets(res.data || []);
    } catch { } finally { setLoading(false); }
  };

  const fetchMessages = async (ticketId: number) => {
    try {
      const res = await api.get<SupportMessage[]>(`/support/tickets/${ticketId}/messages`);
      setMessages(res.data || []);
    } catch { }
  };

  const sendMessage = async () => {
    if (!newMessage.trim() || !selectedTicket) return;
    const content = newMessage;
    setNewMessage('');
    try {
      await api.post(`/support/tickets/${selectedTicket.id}/messages`, { content });
      await fetchMessages(selectedTicket.id);
    } catch { setNewMessage(content); }
  };

  const closeTicket = async (ticketId: number) => {
    try {
      await api.patch(`/support/tickets/${ticketId}/close`);
      fetchTickets();
      if (selectedTicket?.id === ticketId) {
        setSelectedTicket((t) => t ? { ...t, status: 'CLOSED' } : null);
      }
    } catch { alert('Ошибка закрытия'); }
  };

  const assignAdmin = async (ticketId: number) => {
    if (!user?.id) return;
    try {
      await api.patch(`/support/tickets/${ticketId}/assign`, { adminId: user.id });
      fetchTickets();
    } catch { alert('Ошибка назначения'); }
  };

  const statusColors: Record<string, string> = { OPEN: '#4caf50', CLOSED: '#666' };

  return (
    <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
      <div style={{ flex: '0 0 320px', minWidth: '260px' }}>
        <h2 style={{ fontSize: '1.1rem', fontWeight: '600', marginBottom: '1rem' }}>
          Все обращения ({tickets.length})
        </h2>
        {loading && <div style={{ color: '#aaa', padding: '1rem', textAlign: 'center' }}>⏳</div>}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', maxHeight: '600px', overflowY: 'auto' }}>
          {tickets.map((ticket) => (
            <div
              key={ticket.id}
              onClick={() => setSelectedTicket(ticket)}
              style={{
                background: selectedTicket?.id === ticket.id ? '#2a1a1a' : '#1a1a1a',
                border: `1px solid ${selectedTicket?.id === ticket.id ? '#e50914' : '#2a2a2a'}`,
                borderRadius: '8px',
                padding: '0.8rem 1rem',
                cursor: 'pointer',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.3rem' }}>
                <span style={{ fontWeight: '600', fontSize: '0.9rem' }}>#{ticket.id}</span>
                <span style={{ fontSize: '0.75rem', color: statusColors[ticket.status] }}>
                  {ticket.status}
                </span>
              </div>
              <div style={{ color: '#ccc', fontSize: '0.85rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {ticket.subject}
              </div>
              <div style={{ color: '#666', fontSize: '0.75rem', marginTop: '0.3rem' }}>
                Клиент #{ticket.clientId}
                {ticket.adminId ? ` | Админ #${ticket.adminId}` : ' | Не назначен'}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div style={{ flex: 1, minWidth: '300px' }}>
        {!selectedTicket ? (
          <div style={{ height: '400px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#111', borderRadius: '10px', border: '1px solid #2a2a2a', color: '#555', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ fontSize: '3rem' }}>💬</div>
            <p>Выберите обращение</p>
          </div>
        ) : (
          <div style={{ background: '#111', borderRadius: '10px', border: '1px solid #2a2a2a', overflow: 'hidden' }}>
            <div style={{ padding: '1rem 1.2rem', borderBottom: '1px solid #222' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '0.5rem' }}>
                <div>
                  <div style={{ fontWeight: '600' }}>#{selectedTicket.id}: {selectedTicket.subject}</div>
                  <div style={{ fontSize: '0.8rem', color: '#aaa', marginTop: '0.2rem' }}>Клиент #{selectedTicket.clientId}</div>
                </div>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  {!selectedTicket.adminId && (
                    <button onClick={() => assignAdmin(selectedTicket.id)} style={{ ...btnSecondary, fontSize: '0.8rem', padding: '0.4rem 0.8rem' }}>
                      Взять
                    </button>
                  )}
                  {selectedTicket.status === 'OPEN' && (
                    <button onClick={() => closeTicket(selectedTicket.id)} style={{ ...btnDanger, padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}>
                      Закрыть
                    </button>
                  )}
                </div>
              </div>
            </div>

            <div style={{ height: '350px', overflowY: 'auto', padding: '1rem' }}>
              {messages.length === 0 && (
                <div style={{ textAlign: 'center', color: '#666', padding: '2rem' }}>Нет сообщений</div>
              )}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
                {messages.map((msg) => {
                  const isAdmin = msg.senderId === selectedTicket.adminId;
                  return (
                    <div key={msg.id} style={{ display: 'flex', justifyContent: isAdmin ? 'flex-end' : 'flex-start' }}>
                      <div style={{
                        maxWidth: '70%',
                        background: isAdmin ? '#0d1f3c' : '#1a1a1a',
                        border: `1px solid ${isAdmin ? '#1a73e8' : '#2a2a2a'}`,
                        borderRadius: isAdmin ? '12px 12px 0 12px' : '12px 12px 12px 0',
                        padding: '0.7rem 1rem',
                      }}>
                        <div style={{ color: '#888', fontSize: '0.75rem', marginBottom: '0.3rem' }}>
                          {isAdmin ? 'Поддержка' : `Клиент #${msg.senderId}`}
                        </div>
                        <div style={{ color: '#ddd', fontSize: '0.9rem' }}>{msg.content}</div>
                        <div style={{ color: '#555', fontSize: '0.75rem', marginTop: '0.3rem' }}>
                          {new Date(msg.sentAt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {selectedTicket.status === 'OPEN' && (
              <div style={{ padding: '0.8rem 1rem', borderTop: '1px solid #222', display: 'flex', gap: '0.7rem' }}>
                <input
                  type="text"
                  value={newMessage}
                  onChange={(e) => setNewMessage(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                  placeholder="Ответить клиенту..."
                  style={{ flex: 1, background: '#1a1a1a', border: '1px solid #333', borderRadius: '6px', color: '#fff', padding: '0.6rem 0.8rem', fontSize: '0.9rem' }}
                />
                <button onClick={sendMessage} disabled={!newMessage.trim()} style={{ ...btnPrimary, padding: '0.6rem 1rem' }}>
                  Отправить
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

// -------------------- MAIN ADMIN PAGE --------------------
export default function AdminPage() {
  const [activeTab, setActiveTab] = useState<Tab>('movies');

  return (
    <div>
      <h1 style={{ fontSize: '1.8rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Панель администратора</h1>
      <p style={{ color: '#aaa', marginBottom: '2rem' }}>Управление контентом и пользователями</p>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: '0', marginBottom: '2rem', borderBottom: '2px solid #2a2a2a', overflowX: 'auto' }}>
        {TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            style={{
              background: 'transparent',
              border: 'none',
              color: activeTab === tab.key ? '#e50914' : '#666',
              borderBottom: activeTab === tab.key ? '2px solid #e50914' : '2px solid transparent',
              marginBottom: '-2px',
              padding: '0.7rem 1.5rem',
              fontSize: '0.95rem',
              fontWeight: activeTab === tab.key ? '700' : '400',
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              transition: 'color 0.2s',
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div>
        {activeTab === 'movies' && <MoviesTab />}
        {activeTab === 'halls' && <HallsTab />}
        {activeTab === 'sessions' && <SessionsTab />}
        {activeTab === 'menu' && <MenuTab />}
        {activeTab === 'support' && <SupportAdminTab />}
      </div>
    </div>
  );
}
