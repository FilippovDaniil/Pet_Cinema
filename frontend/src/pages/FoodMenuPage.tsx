import { useState, useEffect } from 'react';
import api from '../api/axios';
import { FoodItem } from '../types';

const CATEGORY_META: Record<string, { label: string; icon: string; color: string }> = {
  POPCORN: { label: 'Попкорн',   icon: '🍿', color: '#f5a623' },
  DRINK:   { label: 'Напитки',   icon: '🥤', color: '#1a73e8' },
  SNACK:   { label: 'Закуски',   icon: '🌮', color: '#0d7a4e' },
  OTHER:   { label: 'Другое',    icon: '🍽️', color: '#7b1fa2' },
};

const CATEGORY_ORDER = ['POPCORN', 'DRINK', 'SNACK', 'OTHER'];

export default function FoodMenuPage() {
  const [items, setItems] = useState<FoodItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get<FoodItem[]>('/food-menu')
      .then((r) => setItems(r.data))
      .catch(() => setError('Не удалось загрузить меню'))
      .finally(() => setLoading(false));
  }, []);

  const grouped = CATEGORY_ORDER.reduce<Record<string, FoodItem[]>>((acc, cat) => {
    acc[cat] = items.filter((i) => i.category === cat);
    return acc;
  }, {});

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '4rem', color: '#aaa' }}>
        <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>⏳</div>
        Загрузка меню...
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

  return (
    <div>
      <div style={{ marginBottom: '2.5rem' }}>
        <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Меню кинобара</h1>
        <p style={{ color: '#aaa' }}>Закажите перекус к вашему сеансу</p>
      </div>

      {CATEGORY_ORDER.map((cat) => {
        const catItems = grouped[cat];
        if (catItems.length === 0) return null;
        const meta = CATEGORY_META[cat];
        return (
          <section key={cat} style={{ marginBottom: '3rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
              <span style={{ fontSize: '1.8rem' }}>{meta.icon}</span>
              <h2 style={{
                fontSize: '1.3rem',
                fontWeight: '700',
                borderBottom: `2px solid ${meta.color}`,
                paddingBottom: '0.4rem',
                flex: 1,
              }}>
                {meta.label}
              </h2>
            </div>

            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
              gap: '1.2rem',
            }}>
              {catItems.map((item) => (
                <div
                  key={item.id}
                  style={{
                    background: '#1a1a1a',
                    border: '1px solid #2a2a2a',
                    borderRadius: '12px',
                    padding: '1.5rem',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '0.6rem',
                    transition: 'border-color 0.2s, transform 0.2s',
                  }}
                  onMouseEnter={(e) => {
                    (e.currentTarget as HTMLDivElement).style.borderColor = meta.color;
                    (e.currentTarget as HTMLDivElement).style.transform = 'translateY(-2px)';
                  }}
                  onMouseLeave={(e) => {
                    (e.currentTarget as HTMLDivElement).style.borderColor = '#2a2a2a';
                    (e.currentTarget as HTMLDivElement).style.transform = 'translateY(0)';
                  }}
                >
                  <div style={{ fontSize: '2.5rem', textAlign: 'center', lineHeight: 1 }}>
                    {meta.icon}
                  </div>
                  <h3 style={{ fontSize: '1rem', fontWeight: '600', textAlign: 'center', color: '#fff' }}>
                    {item.name}
                  </h3>
                  <div style={{
                    marginTop: 'auto',
                    textAlign: 'center',
                    fontSize: '1.4rem',
                    fontWeight: '700',
                    color: meta.color,
                  }}>
                    {item.price} ₽
                  </div>
                </div>
              ))}
            </div>
          </section>
        );
      })}
    </div>
  );
}
