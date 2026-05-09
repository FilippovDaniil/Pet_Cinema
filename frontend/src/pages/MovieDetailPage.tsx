import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api/axios';
import { Movie, Review, Comment } from '../types';
import { useAuth } from '../context/AuthContext';

function StarRating({ rating, onChange }: { rating: number; onChange?: (r: number) => void }) {
  const [hovered, setHovered] = useState(0);
  return (
    <span>
      {[1, 2, 3, 4, 5].map((s) => (
        <span
          key={s}
          onClick={() => onChange && onChange(s)}
          onMouseEnter={() => onChange && setHovered(s)}
          onMouseLeave={() => onChange && setHovered(0)}
          style={{
            color: s <= (hovered || Math.round(rating)) ? '#f5a623' : '#555',
            fontSize: onChange ? '1.5rem' : '1rem',
            cursor: onChange ? 'pointer' : 'default',
          }}
        >
          ★
        </span>
      ))}
    </span>
  );
}

export default function MovieDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { isClient, user } = useAuth();

  const [movie, setMovie] = useState<Movie | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');
  const [commentText, setCommentText] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [reviewError, setReviewError] = useState('');

  useEffect(() => {
    if (!id) return;
    const fetchAll = async () => {
      setLoading(true);
      try {
        const [movieRes, reviewsRes, commentsRes] = await Promise.all([
          api.get<Movie>(`/movies/${id}`),
          api.get<Review[]>(`/movies/${id}/reviews`).catch(() => ({ data: [] })),
          api.get<Comment[]>(`/movies/${id}/comments`).catch(() => ({ data: [] })),
        ]);
        setMovie(movieRes.data);
        setReviews(reviewsRes.data);
        setComments(commentsRes.data);
      } catch {
        setError('Не удалось загрузить информацию о фильме');
      } finally {
        setLoading(false);
      }
    };
    fetchAll();
  }, [id]);

  const submitReview = async () => {
    if (!id) return;
    setSubmitting(true);
    setReviewError('');
    try {
      await api.post(`/movies/${id}/reviews`, { rating: reviewRating, comment: reviewComment });
      const res = await api.get<Review[]>(`/movies/${id}/reviews`);
      setReviews(res.data);
      setReviewComment('');
      setReviewRating(5);
    } catch (e: any) {
      setReviewError(e.response?.data?.message || 'Ошибка при отправке отзыва');
    } finally {
      setSubmitting(false);
    }
  };

  const submitComment = async () => {
    if (!id || !commentText.trim()) return;
    setSubmitting(true);
    try {
      await api.post(`/movies/${id}/comments`, { text: commentText });
      const res = await api.get<Comment[]>(`/movies/${id}/comments`);
      setComments(res.data);
      setCommentText('');
    } catch {
    } finally {
      setSubmitting(false);
    }
  };

  const POSTER_GRADIENTS: Record<string, string> = {
    TWO_D:   'linear-gradient(160deg, #0f2027 0%, #203a43 50%, #2c5364 100%)',
    THREE_D: 'linear-gradient(160deg, #0a2e1a 0%, #0d5c32 50%, #11998e 100%)',
    FIVE_D:  'linear-gradient(160deg, #1a0533 0%, #4a1063 50%, #8b2fc9 100%)',
  };

  const typeBadgeColor: Record<string, string> = {
    TWO_D: '#1a73e8',
    THREE_D: '#0d7a4e',
    FIVE_D: '#7b1fa2',
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '4rem', color: '#aaa' }}>
        <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>⏳</div>
        Загрузка...
      </div>
    );
  }

  if (error || !movie) {
    return (
      <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '8px', padding: '1.5rem', color: '#ff6b6b' }}>
        {error || 'Фильм не найден'}
      </div>
    );
  }

  const posterGradient = POSTER_GRADIENTS[movie.type] ?? 'linear-gradient(160deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)';

  return (
    <div>
      {/* Back button */}
      <button
        onClick={() => navigate(-1)}
        style={{ background: 'transparent', border: 'none', color: '#aaa', fontSize: '0.9rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.4rem' }}
      >
        ← Назад
      </button>

      {/* Movie Info */}
      <div style={{ display: 'flex', gap: '2rem', marginBottom: '3rem', flexWrap: 'wrap' }}>
        <div style={{ flexShrink: 0, width: '280px', aspectRatio: '2/3', borderRadius: '12px', overflow: 'hidden', position: 'relative', background: posterGradient }}>
          {/* Fallback content */}
          <div style={{
            position: 'absolute', inset: 0,
            display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center',
            gap: '1rem', padding: '2rem',
          }}>
            <span style={{ fontSize: '4rem', lineHeight: 1 }}>🎬</span>
            <span style={{ color: 'rgba(255,255,255,0.6)', fontSize: '0.95rem', textAlign: 'center', lineHeight: 1.4 }}>
              {movie.title}
            </span>
          </div>
          {/* Real poster on top */}
          {movie.posterUrl && (
            <img
              src={movie.posterUrl}
              alt={movie.title}
              style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
              onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
            />
          )}
        </div>
        <div style={{ flex: 1, minWidth: '280px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '0.8rem', flexWrap: 'wrap' }}>
            <h1 style={{ fontSize: '2rem', fontWeight: 'bold' }}>{movie.title}</h1>
            <span style={{
              background: typeBadgeColor[movie.type] || '#444',
              color: '#fff',
              fontSize: '0.8rem',
              fontWeight: '700',
              padding: '3px 10px',
              borderRadius: '4px',
            }}>
              {movie.type?.replace('_', '') || ''}
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem', marginBottom: '1rem' }}>
            <StarRating rating={movie.averageRating} />
            <span style={{ color: '#aaa', fontSize: '0.9rem' }}>
              {movie.averageRating ? movie.averageRating.toFixed(1) : 'Нет оценок'}
            </span>
          </div>

          <div style={{ color: '#aaa', marginBottom: '1rem', fontSize: '0.95rem' }}>
            <span style={{ marginRight: '1.5rem' }}>⏱ {movie.durationMinutes} мин</span>
          </div>

          {movie.genres && movie.genres.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem', marginBottom: '1.2rem' }}>
              {movie.genres.map((g, i) => (
                <span key={i} style={{ background: '#1a1a1a', border: '1px solid #333', color: '#ddd', fontSize: '0.85rem', padding: '3px 10px', borderRadius: '4px' }}>
                  {g}
                </span>
              ))}
            </div>
          )}

          <p style={{ color: '#ccc', lineHeight: '1.7', marginBottom: '2rem', maxWidth: '600px' }}>
            {movie.description}
          </p>

          <button
            onClick={() => navigate(`/sessions/${movie.id}`)}
            style={{
              background: '#e50914',
              color: '#fff',
              border: 'none',
              borderRadius: '8px',
              padding: '0.8rem 2rem',
              fontSize: '1rem',
              fontWeight: '700',
              letterSpacing: '0.5px',
            }}
          >
            Выбрать сеанс →
          </button>
        </div>
      </div>

      {/* Reviews Section */}
      <section style={{ marginBottom: '3rem' }}>
        <h2 style={{ fontSize: '1.4rem', fontWeight: '700', marginBottom: '1.5rem', borderBottom: '2px solid #e50914', paddingBottom: '0.5rem' }}>
          Отзывы ({reviews.length})
        </h2>

        {isClient && (
          <div style={{ background: '#1a1a1a', borderRadius: '10px', padding: '1.5rem', marginBottom: '1.5rem', border: '1px solid #2a2a2a' }}>
            <h3 style={{ fontSize: '1rem', marginBottom: '1rem' }}>Написать отзыв</h3>
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
              <span style={{ color: '#aaa', fontSize: '0.9rem' }}>Оценка:</span>
              <StarRating rating={reviewRating} onChange={setReviewRating} />
              <span style={{ color: '#f5a623' }}>{reviewRating}/5</span>
            </div>
            <textarea
              value={reviewComment}
              onChange={(e) => setReviewComment(e.target.value)}
              placeholder="Ваш отзыв..."
              rows={3}
              style={{
                width: '100%',
                background: '#111',
                border: '1px solid #333',
                borderRadius: '6px',
                color: '#fff',
                padding: '0.7rem',
                fontSize: '0.9rem',
                resize: 'vertical',
                marginBottom: '0.8rem',
              }}
            />
            {reviewError && <div style={{ color: '#ff6b6b', fontSize: '0.85rem', marginBottom: '0.8rem' }}>{reviewError}</div>}
            <button
              onClick={submitReview}
              disabled={submitting}
              style={{
                background: '#e50914',
                color: '#fff',
                border: 'none',
                borderRadius: '6px',
                padding: '0.6rem 1.5rem',
                fontWeight: '600',
                opacity: submitting ? 0.7 : 1,
              }}
            >
              {submitting ? 'Отправка...' : 'Отправить отзыв'}
            </button>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {reviews.length === 0 && (
            <p style={{ color: '#666', fontStyle: 'italic' }}>Отзывов пока нет. Будьте первым!</p>
          )}
          {reviews.map((review) => (
            <div key={review.id} style={{ background: '#1a1a1a', borderRadius: '8px', padding: '1rem', border: '1px solid #2a2a2a' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem', marginBottom: '0.5rem' }}>
                <span style={{ background: '#2a2a2a', borderRadius: '50%', width: '32px', height: '32px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.85rem', color: '#aaa' }}>
                  👤
                </span>
                <span style={{ color: '#aaa', fontSize: '0.85rem' }}>Пользователь #{review.userId}</span>
                <span style={{ marginLeft: 'auto', color: '#666', fontSize: '0.8rem' }}>
                  {new Date(review.createdAt).toLocaleDateString('ru-RU')}
                </span>
              </div>
              <StarRating rating={review.rating} />
              {review.comment && (
                <p style={{ color: '#ccc', marginTop: '0.5rem', fontSize: '0.9rem', lineHeight: '1.5' }}>{review.comment}</p>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* Comments Section */}
      <section>
        <h2 style={{ fontSize: '1.4rem', fontWeight: '700', marginBottom: '1.5rem', borderBottom: '2px solid #333', paddingBottom: '0.5rem' }}>
          Комментарии ({comments.length})
        </h2>

        {isClient && (
          <div style={{ display: 'flex', gap: '0.8rem', marginBottom: '1.5rem' }}>
            <input
              type="text"
              value={commentText}
              onChange={(e) => setCommentText(e.target.value)}
              placeholder="Написать комментарий..."
              onKeyDown={(e) => e.key === 'Enter' && submitComment()}
              style={{
                flex: 1,
                background: '#1a1a1a',
                border: '1px solid #333',
                borderRadius: '6px',
                color: '#fff',
                padding: '0.7rem 1rem',
                fontSize: '0.9rem',
              }}
            />
            <button
              onClick={submitComment}
              disabled={submitting || !commentText.trim()}
              style={{
                background: '#e50914',
                color: '#fff',
                border: 'none',
                borderRadius: '6px',
                padding: '0.7rem 1.2rem',
                fontWeight: '600',
                opacity: (submitting || !commentText.trim()) ? 0.6 : 1,
              }}
            >
              Отправить
            </button>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
          {comments.length === 0 && (
            <p style={{ color: '#666', fontStyle: 'italic' }}>Комментариев пока нет.</p>
          )}
          {comments.map((comment) => (
            <div key={comment.id} style={{ background: '#111', borderRadius: '8px', padding: '0.8rem 1rem', border: '1px solid #222', display: 'flex', gap: '0.8rem' }}>
              <span style={{ background: '#1a1a1a', borderRadius: '50%', width: '32px', height: '32px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.85rem', color: '#aaa', flexShrink: 0 }}>
                👤
              </span>
              <div>
                <div style={{ color: '#aaa', fontSize: '0.8rem', marginBottom: '0.3rem' }}>
                  Пользователь #{comment.userId} · {new Date(comment.createdAt).toLocaleDateString('ru-RU')}
                </div>
                <p style={{ color: '#ddd', fontSize: '0.9rem' }}>{comment.text}</p>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
