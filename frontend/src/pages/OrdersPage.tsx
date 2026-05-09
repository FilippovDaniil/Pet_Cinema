import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/axios';
import { Order } from '../types';

const statusColors: Record<string, string> = {
  PENDING: '#f5a623',
  PAID: '#4caf50',
  CANCELLED: '#e50914',
};

const statusLabels: Record<string, string> = {
  PENDING: 'Ожидает оплаты',
  PAID: 'Оплачен',
  CANCELLED: 'Отменён',
};

const orderTypeLabels: Record<string, string> = {
  TICKET: '🎟 Билет',
  FOOD: '🍿 Еда',
  MIXED: '🎟🍿 Билет + Еда',
};

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedOrder, setExpandedOrder] = useState<number | null>(null);

  useEffect(() => {
    api.get<Order[]>('/orders/my')
      .then((r) => setOrders(r.data || []))
      .catch(() => setError('Не удалось загрузить заказы'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '1.8rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Мои заказы</h1>
      <p style={{ color: '#aaa', marginBottom: '2rem' }}>История всех ваших покупок</p>

      {loading && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#aaa' }}>
          <div style={{ fontSize: '2rem', marginBottom: '1rem' }}>⏳</div>
          Загрузка заказов...
        </div>
      )}

      {error && (
        <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '8px', padding: '1.5rem', color: '#ff6b6b' }}>
          {error}
        </div>
      )}

      {!loading && orders.length === 0 && !error && (
        <div style={{ textAlign: 'center', padding: '4rem', color: '#666', background: '#111', borderRadius: '12px', border: '1px solid #1a1a1a' }}>
          <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>🎟</div>
          <p style={{ marginBottom: '0.5rem', fontSize: '1.1rem' }}>У вас пока нет заказов</p>
          <p style={{ fontSize: '0.9rem', marginBottom: '1.5rem' }}>Купите билет или закажите еду из нашего меню</p>
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link to="/">
              <button style={{ background: '#e50914', color: '#fff', border: 'none', borderRadius: '8px', padding: '0.7rem 1.5rem', fontWeight: '600', cursor: 'pointer' }}>
                Смотреть афишу
              </button>
            </Link>
            <Link to="/food">
              <button style={{ background: 'transparent', color: '#e50914', border: '1.5px solid #e50914', borderRadius: '8px', padding: '0.7rem 1.5rem', fontWeight: '600', cursor: 'pointer' }}>
                Заказать еду
              </button>
            </Link>
          </div>
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        {orders.map((order) => (
          <div key={order.id} style={{ background: '#1a1a1a', borderRadius: '10px', border: '1px solid #2a2a2a', overflow: 'hidden' }}>
            <div
              style={{ padding: '1.2rem', cursor: 'pointer', display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '1rem' }}
              onClick={() => setExpandedOrder(expandedOrder === order.id ? null : order.id)}
            >
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.8rem', flexWrap: 'wrap' }}>
                  <span style={{ fontWeight: '600' }}>Заказ #{order.id}</span>
                  <span style={{
                    background: statusColors[order.status] + '22',
                    color: statusColors[order.status],
                    border: `1px solid ${statusColors[order.status]}`,
                    fontSize: '0.75rem',
                    fontWeight: '700',
                    padding: '2px 8px',
                    borderRadius: '4px',
                  }}>
                    {statusLabels[order.status] || order.status}
                  </span>
                  <span style={{ color: '#aaa', fontSize: '0.85rem' }}>
                    {orderTypeLabels[order.orderType] || order.orderType}
                  </span>
                </div>
                <div style={{ color: '#666', fontSize: '0.8rem', marginTop: '0.3rem' }}>
                  {new Date(order.createdAt).toLocaleString('ru-RU')}
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                <span style={{ fontSize: '1.2rem', fontWeight: '700', color: '#e50914' }}>
                  {Number(order.totalPrice).toFixed(2)} ₽
                </span>
                <span style={{ color: '#666', fontSize: '0.85rem' }}>{expandedOrder === order.id ? '▲' : '▼'}</span>
              </div>
            </div>

            {expandedOrder === order.id && order.items && order.items.length > 0 && (
              <div style={{ padding: '0 1.2rem 1.2rem', borderTop: '1px solid #2a2a2a' }}>
                <div style={{ paddingTop: '1rem' }}>
                  <div style={{ color: '#888', fontSize: '0.85rem', marginBottom: '0.7rem' }}>Состав заказа:</div>
                  {order.items.map((item) => (
                    <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid #222', fontSize: '0.9rem' }}>
                      <div>
                        {item.itemType === 'TICKET' ? (
                          <span>🎟 Билет — Ряд {item.ticketSeatRow}, Место {item.ticketSeatNumber}</span>
                        ) : (
                          <span>🍿 Позиция меню × {item.quantity}</span>
                        )}
                      </div>
                      <div style={{ color: '#e50914', fontWeight: '600' }}>{Number(item.price).toFixed(2)} ₽</div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
