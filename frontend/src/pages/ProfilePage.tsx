import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/axios';
import { useAuth } from '../context/AuthContext';
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
  TICKET: 'Билет',
  FOOD: 'Еда',
  MIXED: 'Билет + Еда',
};

export default function ProfilePage() {
  const { user, logout } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [expandedOrder, setExpandedOrder] = useState<number | null>(null);

  useEffect(() => {
    const fetchOrders = async () => {
      setLoading(true);
      try {
        const res = await api.get<Order[]>('/orders/my');
        setOrders(res.data || []);
      } catch {
        setError('Не удалось загрузить заказы');
      } finally {
        setLoading(false);
      }
    };
    fetchOrders();
  }, []);

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

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      {/* User Card */}
      <div style={{
        background: '#1a1a1a',
        borderRadius: '12px',
        padding: '2rem',
        marginBottom: '2rem',
        border: '1px solid #2a2a2a',
        display: 'flex',
        alignItems: 'center',
        gap: '1.5rem',
        flexWrap: 'wrap',
      }}>
        <div style={{
          width: '70px',
          height: '70px',
          borderRadius: '50%',
          background: '#e50914',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '2rem',
          flexShrink: 0,
        }}>
          👤
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap', marginBottom: '0.5rem' }}>
            <h1 style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>
              {user?.username || `Пользователь #${user?.id}`}
            </h1>
            {user?.role && (
              <span style={{
                background: roleColors[user.role] || '#444',
                color: '#fff',
                fontSize: '0.75rem',
                fontWeight: '700',
                padding: '3px 10px',
                borderRadius: '4px',
              }}>
                {roleLabels[user.role] || user.role}
              </span>
            )}
          </div>
          {user?.email && (
            <div style={{ color: '#aaa', fontSize: '0.9rem' }}>{user.email}</div>
          )}
          <div style={{ color: '#666', fontSize: '0.85rem', marginTop: '0.3rem' }}>
            ID: #{user?.id}
          </div>
        </div>
        <div style={{ display: 'flex', gap: '0.8rem', flexWrap: 'wrap' }}>
          <Link to="/support">
            <button style={{
              background: 'transparent',
              color: '#aaa',
              border: '1.5px solid #444',
              borderRadius: '8px',
              padding: '0.6rem 1.2rem',
              fontWeight: '600',
              fontSize: '0.9rem',
            }}>
              Поддержка
            </button>
          </Link>
          <button
            onClick={logout}
            style={{
              background: 'transparent',
              color: '#e50914',
              border: '1.5px solid #e50914',
              borderRadius: '8px',
              padding: '0.6rem 1.2rem',
              fontWeight: '600',
              fontSize: '0.9rem',
            }}
          >
            Выйти
          </button>
        </div>
      </div>

      {/* Orders Section */}
      <div>
        <h2 style={{ fontSize: '1.3rem', fontWeight: '700', marginBottom: '1.5rem', borderBottom: '2px solid #e50914', paddingBottom: '0.5rem' }}>
          История заказов
        </h2>

        {loading && (
          <div style={{ textAlign: 'center', padding: '2rem', color: '#aaa' }}>
            ⏳ Загрузка заказов...
          </div>
        )}

        {error && (
          <div style={{ background: '#2a0a0a', border: '1px solid #e50914', borderRadius: '8px', padding: '1rem', color: '#ff6b6b' }}>
            {error}
          </div>
        )}

        {!loading && orders.length === 0 && !error && (
          <div style={{ textAlign: 'center', padding: '3rem', color: '#666' }}>
            <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🎟</div>
            <p>У вас пока нет заказов</p>
            <Link to="/">
              <button style={{
                marginTop: '1rem',
                background: '#e50914',
                color: '#fff',
                border: 'none',
                borderRadius: '8px',
                padding: '0.7rem 1.5rem',
                fontWeight: '600',
              }}>
                Перейти к афише
              </button>
            </Link>
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {orders.map((order) => (
            <div key={order.id} style={{
              background: '#1a1a1a',
              borderRadius: '10px',
              border: '1px solid #2a2a2a',
              overflow: 'hidden',
            }}>
              <div
                style={{
                  padding: '1.2rem',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: '1rem',
                }}
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
                    {order.totalPrice} ₽
                  </span>
                  <span style={{ color: '#666' }}>{expandedOrder === order.id ? '▲' : '▼'}</span>
                </div>
              </div>

              {expandedOrder === order.id && order.items && order.items.length > 0 && (
                <div style={{ padding: '0 1.2rem 1.2rem', borderTop: '1px solid #2a2a2a' }}>
                  <div style={{ paddingTop: '1rem' }}>
                    <div style={{ color: '#888', fontSize: '0.85rem', marginBottom: '0.7rem' }}>Состав заказа:</div>
                    {order.items.map((item) => (
                      <div key={item.id} style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        padding: '0.5rem 0',
                        borderBottom: '1px solid #222',
                        fontSize: '0.9rem',
                      }}>
                        <div>
                          {item.itemType === 'TICKET' ? (
                            <span>🎟 Билет — Ряд {item.ticketSeatRow}, место {item.ticketSeatNumber}</span>
                          ) : (
                            <span>🍿 Еда (ID: {item.foodItemId}) × {item.quantity}</span>
                          )}
                        </div>
                        <div style={{ color: '#e50914', fontWeight: '600' }}>{item.price} ₽</div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
