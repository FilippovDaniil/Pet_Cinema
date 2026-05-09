import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
import { Movie } from '../types';

interface UserProfile {
  id: number;
  username: string;
  email: string;
  role: string;
}

const roleLabels: Record<string, string> = {
  ROLE_CLIENT: 'Клиент',
  ROLE_SELLER: 'Продавец',
  ROLE_ADMIN: 'Администратор',
};

const roleColors: Record<string, string> = {
  ROLE_CLIENT: '#1a73e8',
  ROLE_SELLER: '#f5a623',
  ROLE_ADMIN: '#e50914',
};

const POSTER_GRADIENTS: Record<string, string> = {
  TWO_D:   'linear-gradient(160deg, #0f2027 0%, #203a43 50%, #2c5364 100%)',
  THREE_D: 'linear-gradient(160deg, #0a2e1a 0%, #0d5c32 50%, #11998e 100%)',
  FIVE_D:  'linear-gradient(160deg, #1a0533 0%, #4a1063 50%, #8b2fc9 100%)',
};

export default function ProfilePage() {
  const { user, logout } = useAuth();

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [editing, setEditing] = useState(false);
  const [editUsername, setEditUsername] = useState('');
  const [editEmail, setEditEmail] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [saveSuccess, setSaveSuccess] = useState(false);

  const [favMovieIds, setFavMovieIds] = useState<number[]>([]);
  const [favMovies, setFavMovies] = useState<Movie[]>([]);
  const [allMovies, setAllMovies] = useState<Movie[]>([]);
  const [showMoviePicker, setShowMoviePicker] = useState(false);

  useEffect(() => {
    api.get<UserProfile>('/auth/me')
      .then((r) => {
        setProfile(r.data);
        setEditUsername(r.data.username);
        setEditEmail(r.data.email);
      })
      .catch(() => {
        if (user) {
          setProfile({ id: user.id, username: `Пользователь #${user.id}`, email: '', role: user.role });
        }
      });
  }, [user]);

  useEffect(() => {
    if (!user) return;
    const stored = localStorage.getItem(`favMovies_${user.id}`);
    const ids: number[] = stored ? JSON.parse(stored) : [];
    setFavMovieIds(ids);
  }, [user]);

  useEffect(() => {
    if (favMovieIds.length === 0) { setFavMovies([]); return; }
    Promise.all(favMovieIds.map((id) => api.get<Movie>(`/movies/${id}`).then((r) => r.data).catch(() => null)))
      .then((movies) => setFavMovies(movies.filter(Boolean) as Movie[]));
  }, [favMovieIds]);

  const saveProfile = async () => {
    if (!profile) return;
    setSaving(true);
    setSaveError('');
    setSaveSuccess(false);
    try {
      const res = await api.patch<UserProfile>('/auth/me', {
        username: editUsername !== profile.username ? editUsername : undefined,
        email: editEmail !== profile.email ? editEmail : undefined,
      });
      setProfile(res.data);
      setEditing(false);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch (e: any) {
      setSaveError(e.response?.data?.message || 'Ошибка при сохранении');
    } finally {
      setSaving(false);
    }
  };

  const toggleFavMovie = (movieId: number) => {
    if (!user) return;
    const next = favMovieIds.includes(movieId)
      ? favMovieIds.filter((id) => id !== movieId)
      : [...favMovieIds, movieId];
    setFavMovieIds(next);
    localStorage.setItem(`favMovies_${user.id}`, JSON.stringify(next));
  };

  const loadAllMovies = async () => {
    if (allMovies.length > 0) { setShowMoviePicker(true); return; }
    try {
      const res = await api.get('/movies', { params: { size: 100 } });
      setAllMovies(res.data.content || res.data || []);
      setShowMoviePicker(true);
    } catch {}
  };

  const inputStyle: React.CSSProperties = {
    background: '#111',
    border: '1.5px solid #333',
    borderRadius: '6px',
    color: '#fff',
    padding: '0.6rem 0.8rem',
    fontSize: '0.9rem',
    width: '100%',
  };

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      {/* Profile Card */}
      <div style={{ background: '#1a1a1a', borderRadius: '12px', padding: '2rem', marginBottom: '2rem', border: '1px solid #2a2a2a' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '1.5rem', flexWrap: 'wrap' }}>
          <div style={{ width: '70px', height: '70px', borderRadius: '50%', background: '#e50914', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '2rem', flexShrink: 0 }}>
            👤
          </div>
          <div style={{ flex: 1, minWidth: '200px' }}>
            {editing ? (
              <div>
                <div style={{ marginBottom: '0.7rem' }}>
                  <label style={{ color: '#888', fontSize: '0.8rem', display: 'block', marginBottom: '0.3rem' }}>Имя пользователя</label>
                  <input style={inputStyle} value={editUsername} onChange={(e) => setEditUsername(e.target.value)} />
                </div>
                <div style={{ marginBottom: '1rem' }}>
                  <label style={{ color: '#888', fontSize: '0.8rem', display: 'block', marginBottom: '0.3rem' }}>Email</label>
                  <input style={inputStyle} type="email" value={editEmail} onChange={(e) => setEditEmail(e.target.value)} />
                </div>
                {saveError && <div style={{ color: '#ff6b6b', fontSize: '0.85rem', marginBottom: '0.8rem' }}>{saveError}</div>}
                <div style={{ display: 'flex', gap: '0.7rem' }}>
                  <button onClick={saveProfile} disabled={saving} style={{ background: '#e50914', color: '#fff', border: 'none', borderRadius: '6px', padding: '0.5rem 1.2rem', fontWeight: '600', fontSize: '0.9rem', cursor: 'pointer', opacity: saving ? 0.7 : 1 }}>
                    {saving ? 'Сохранение...' : 'Сохранить'}
                  </button>
                  <button
                    onClick={() => { setEditing(false); setSaveError(''); setEditUsername(profile?.username || ''); setEditEmail(profile?.email || ''); }}
                    style={{ background: 'transparent', color: '#aaa', border: '1px solid #444', borderRadius: '6px', padding: '0.5rem 1rem', fontWeight: '600', fontSize: '0.9rem', cursor: 'pointer' }}
                  >
                    Отмена
                  </button>
                </div>
              </div>
            ) : (
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem', flexWrap: 'wrap', marginBottom: '0.4rem' }}>
                  <h1 style={{ fontSize: '1.4rem', fontWeight: 'bold' }}>{profile?.username || `Пользователь #${user?.id}`}</h1>
                  {profile?.role && (
                    <span style={{ background: roleColors[profile.role] || '#444', color: '#fff', fontSize: '0.75rem', fontWeight: '700', padding: '3px 10px', borderRadius: '4px' }}>
                      {roleLabels[profile.role] || profile.role}
                    </span>
                  )}
                </div>
                {profile?.email && <div style={{ color: '#aaa', fontSize: '0.9rem', marginBottom: '0.3rem' }}>{profile.email}</div>}
                <div style={{ color: '#666', fontSize: '0.8rem' }}>ID: #{profile?.id || user?.id}</div>
                {saveSuccess && <div style={{ color: '#4caf50', fontSize: '0.85rem', marginTop: '0.5rem' }}>Профиль успешно обновлён</div>}
              </div>
            )}
          </div>
          {!editing && (
            <div style={{ display: 'flex', gap: '0.8rem', flexWrap: 'wrap' }}>
              <button onClick={() => setEditing(true)} style={{ background: 'transparent', color: '#aaa', border: '1.5px solid #444', borderRadius: '8px', padding: '0.6rem 1.2rem', fontWeight: '600', fontSize: '0.9rem', cursor: 'pointer' }}>
                Изменить
              </button>
              <Link to="/support">
                <button style={{ background: 'transparent', color: '#aaa', border: '1.5px solid #444', borderRadius: '8px', padding: '0.6rem 1.2rem', fontWeight: '600', fontSize: '0.9rem', cursor: 'pointer' }}>
                  Поддержка
                </button>
              </Link>
              <button onClick={logout} style={{ background: 'transparent', color: '#e50914', border: '1.5px solid #e50914', borderRadius: '8px', padding: '0.6rem 1.2rem', fontWeight: '600', fontSize: '0.9rem', cursor: 'pointer' }}>
                Выйти
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Quick links */}
      <div style={{ display: 'flex', gap: '1rem', marginBottom: '2rem', flexWrap: 'wrap' }}>
        <Link to="/orders" style={{ flex: 1, minWidth: '140px', textDecoration: 'none' }}>
          <div
            style={{ background: '#1a1a1a', border: '1px solid #2a2a2a', borderRadius: '10px', padding: '1.2rem', textAlign: 'center', cursor: 'pointer', transition: 'border-color 0.2s' }}
            onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#e50914')}
            onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#2a2a2a')}
          >
            <div style={{ fontSize: '2rem', marginBottom: '0.4rem' }}>🎟</div>
            <div style={{ fontWeight: '600', fontSize: '0.9rem', color: '#fff' }}>Мои заказы</div>
            <div style={{ color: '#666', fontSize: '0.78rem', marginTop: '0.2rem' }}>История покупок</div>
          </div>
        </Link>
        <Link to="/food" style={{ flex: 1, minWidth: '140px', textDecoration: 'none' }}>
          <div
            style={{ background: '#1a1a1a', border: '1px solid #2a2a2a', borderRadius: '10px', padding: '1.2rem', textAlign: 'center', cursor: 'pointer', transition: 'border-color 0.2s' }}
            onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#f5a623')}
            onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#2a2a2a')}
          >
            <div style={{ fontSize: '2rem', marginBottom: '0.4rem' }}>🍿</div>
            <div style={{ fontWeight: '600', fontSize: '0.9rem', color: '#fff' }}>Меню</div>
            <div style={{ color: '#666', fontSize: '0.78rem', marginTop: '0.2rem' }}>Еда и напитки</div>
          </div>
        </Link>
        <Link to="/" style={{ flex: 1, minWidth: '140px', textDecoration: 'none' }}>
          <div
            style={{ background: '#1a1a1a', border: '1px solid #2a2a2a', borderRadius: '10px', padding: '1.2rem', textAlign: 'center', cursor: 'pointer', transition: 'border-color 0.2s' }}
            onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#1a73e8')}
            onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#2a2a2a')}
          >
            <div style={{ fontSize: '2rem', marginBottom: '0.4rem' }}>🎬</div>
            <div style={{ fontWeight: '600', fontSize: '0.9rem', color: '#fff' }}>Афиша</div>
            <div style={{ color: '#666', fontSize: '0.78rem', marginTop: '0.2rem' }}>Текущие фильмы</div>
          </div>
        </Link>
      </div>

      {/* Favorite movies gallery */}
      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.2rem', borderBottom: '2px solid #333', paddingBottom: '0.5rem' }}>
          <h2 style={{ fontSize: '1.3rem', fontWeight: '700' }}>Избранные фильмы</h2>
          <button
            onClick={loadAllMovies}
            style={{ background: 'transparent', color: '#e50914', border: '1px solid #e50914', borderRadius: '6px', padding: '0.4rem 1rem', fontSize: '0.85rem', fontWeight: '600', cursor: 'pointer' }}
          >
            + Добавить
          </button>
        </div>

        {favMovies.length === 0 && !showMoviePicker && (
          <div style={{ color: '#555', fontStyle: 'italic', padding: '1.5rem 0', textAlign: 'center' }}>
            Добавьте любимые фильмы в галерею
          </div>
        )}

        {favMovies.length > 0 && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
            {favMovies.map((movie) => {
              const gradient = POSTER_GRADIENTS[movie.type] ?? 'linear-gradient(160deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)';
              return (
                <div key={movie.id} style={{ position: 'relative' }}>
                  <Link to={`/movies/${movie.id}`} style={{ textDecoration: 'none' }}>
                    <div style={{ borderRadius: '8px', overflow: 'hidden', aspectRatio: '2/3', background: gradient, position: 'relative' }}>
                      <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '0.5rem' }}>
                        <span style={{ fontSize: '1.8rem' }}>🎬</span>
                        <span style={{ color: 'rgba(255,255,255,0.7)', fontSize: '0.7rem', textAlign: 'center', lineHeight: 1.3, marginTop: '0.3rem' }}>{movie.title}</span>
                      </div>
                      {movie.posterUrl && (
                        <img src={movie.posterUrl} alt={movie.title}
                          style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
                          onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }} />
                      )}
                    </div>
                  </Link>
                  <button
                    onClick={() => toggleFavMovie(movie.id)}
                    title="Убрать из избранного"
                    style={{ position: 'absolute', top: '4px', right: '4px', background: 'rgba(0,0,0,0.75)', border: 'none', color: '#e50914', borderRadius: '50%', width: '22px', height: '22px', fontSize: '0.65rem', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                  >
                    ✕
                  </button>
                  <div style={{ fontSize: '0.72rem', color: '#aaa', marginTop: '0.3rem', textAlign: 'center', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {movie.title}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        {showMoviePicker && (
          <div style={{ background: '#111', border: '1px solid #2a2a2a', borderRadius: '10px', padding: '1.2rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.8rem' }}>
              <span style={{ color: '#aaa', fontSize: '0.9rem' }}>Выберите фильмы для галереи:</span>
              <button onClick={() => setShowMoviePicker(false)} style={{ background: 'none', border: 'none', color: '#666', cursor: 'pointer', fontSize: '1.1rem', lineHeight: 1 }}>✕</button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: '0.5rem', maxHeight: '280px', overflowY: 'auto' }}>
              {allMovies.map((movie) => {
                const isFav = favMovieIds.includes(movie.id);
                return (
                  <div
                    key={movie.id}
                    onClick={() => toggleFavMovie(movie.id)}
                    style={{ background: isFav ? '#1a0a0a' : '#1a1a1a', border: `1px solid ${isFav ? '#e50914' : '#2a2a2a'}`, borderRadius: '6px', padding: '0.6rem 0.8rem', cursor: 'pointer', transition: 'border-color 0.2s', display: 'flex', alignItems: 'center', gap: '0.5rem' }}
                  >
                    <span style={{ fontSize: '0.85rem', color: isFav ? '#fff' : '#ccc', flex: 1 }}>{movie.title}</span>
                    {isFav && <span style={{ color: '#e50914', fontSize: '0.85rem' }}>✓</span>}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
