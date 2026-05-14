import { useState, useEffect } from 'react';
import api from '../api/axios';
import { FoodItem } from '../types';

// useAuth — isClient: только клиент может добавлять в корзину и оформлять заказ.
// isAuthenticated — для редиректа на логин если не залогинен.
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

// CATEGORY_META — метаданные категорий: отображаемое имя, emoji, цвет акцента.
// Используется для цветных заголовков секций и кнопок.
const CATEGORY_META: Record<string, { label: string; icon: string; color: string }> = {
  POPCORN: { label: 'Попкорн',  icon: '🍿', color: '#f5a623' }, // оранжевый
  DRINK:   { label: 'Напитки',  icon: '🥤', color: '#1a73e8' }, // синий
  SNACK:   { label: 'Закуски',  icon: '🌮', color: '#0d7a4e' }, // зелёный
  OTHER:   { label: 'Другое',   icon: '🍽️', color: '#7b1fa2' }, // фиолетовый
};

// CATEGORY_ORDER — порядок отображения категорий в меню.
// Явный порядок: попкорн первым (главный продукт кинотеатра).
const CATEGORY_ORDER = ['POPCORN', 'DRINK', 'SNACK', 'OTHER'];

// FoodMenuPage — страница меню кинобара.
// Публичная страница (видна всем), но добавить в корзину могут только ROLE_CLIENT.
export default function FoodMenuPage() {
  // isClient — проверяем роль для показа кнопок корзины.
  const { isClient, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  const [items, setItems] = useState<FoodItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // cart: Record<number, number> — маппинг itemId → количество в корзине.
  // Пример: { 1: 2, 3: 1 } = 2 попкорна, 1 кола.
  const [cart, setCart] = useState<Record<number, number>>({});
  const [ordering, setOrdering] = useState(false);    // идёт POST запрос
  const [orderSuccess, setOrderSuccess] = useState(false); // успех (4 сек)
  const [orderError, setOrderError] = useState('');

  // Загружаем меню при монтировании.
  useEffect(() => {
    api.get<FoodItem[]>('/food-menu')
      .then((r) => setItems(r.data))
      .catch(() => setError('Не удалось загрузить меню'))
      .finally(() => setLoading(false));
  }, []);

  // addToCart — увеличивает количество товара в корзине на 1.
  // Spread + override: { ...prev, [itemId]: newQty } — создаём новый объект.
  const addToCart = (itemId: number) => {
    setCart((prev) => ({ ...prev, [itemId]: (prev[itemId] || 0) + 1 }));
  };

  // removeFromCart — уменьшает количество; если qty <= 0 — удаляет из корзины.
  const removeFromCart = (itemId: number) => {
    setCart((prev) => {
      const qty = (prev[itemId] || 0) - 1;
      if (qty <= 0) {
        // Удаляем ключ из объекта (не просто ставим 0 — чтобы cartItemCount был точным).
        const next = { ...prev };
        delete next[itemId];
        return next;
      }
      return { ...prev, [itemId]: qty };
    });
  };

  // cartItemCount — общее количество товаров (для бейджа на иконке корзины).
  // Object.values(cart) — массив количеств; reduce суммирует.
  const cartItemCount = Object.values(cart).reduce((s, q) => s + q, 0);

  // cartTotal — итоговая стоимость корзины.
  // Object.entries(cart) → [[id, qty], ...]; find ищет товар по ID.
  const cartTotal = Object.entries(cart).reduce((sum, [id, qty]) => {
    const item = items.find((i) => i.id === parseInt(id));
    return sum + (item ? item.price * qty : 0);
  }, 0);

  // cartLines — массив { item, qty } для отображения в сайдбаре корзины.
  // .filter((l) => l.item) — исключаем товары, которые не нашлись (удалены с бэкенда).
  const cartLines = Object.entries(cart)
    .map(([id, qty]) => ({ item: items.find((i) => i.id === parseInt(id))!, qty }))
    .filter((l) => l.item);

  // placeOrder — отправляет заказ на еду.
  const placeOrder = async () => {
    // Если не залогинен — редиректим на страницу входа.
    if (!isAuthenticated) { navigate('/login'); return; }
    // Только ROLE_CLIENT может заказывать через этот endpoint.
    if (!isClient) return;
    setOrdering(true);
    setOrderError('');
    try {
      // POST /api/orders/food/client — сразу создаётся PAID заказ.
      // items: массив { foodItemId, quantity } — каждый тип товара отдельной строкой.
      await api.post('/orders/food/client', {
        items: Object.entries(cart).map(([id, qty]) => ({ foodItemId: parseInt(id), quantity: qty })),
      });
      setCart({});           // очищаем корзину после успешного заказа
      setOrderSuccess(true);
      setTimeout(() => setOrderSuccess(false), 4000); // скрыть сообщение через 4 сек
    } catch (e: any) {
      setOrderError(e.response?.data?.message || 'Ошибка при оформлении заказа');
    } finally {
      setOrdering(false);
    }
  };

  // grouped — товары сгруппированные по категориям в нужном порядке.
  // CATEGORY_ORDER.reduce: начинаем с пустого объекта, заполняем каждую категорию.
  const grouped = CATEGORY_ORDER.reduce<Record<string, FoodItem[]>>((acc, cat) => {
    acc[cat] = items.filter((i) => i.category === cat);
    return acc;
  }, {});

  // Экраны загрузки и ошибки.
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
    // Двухколоночный layout: меню (flex:1) + корзина (280px, только для клиентов).
    <div style={{ display: 'flex', gap: '2rem', alignItems: 'flex-start' }}>

      {/* Левая колонка: меню по категориям. */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ marginBottom: '2rem' }}>
          <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Меню кинобара</h1>
          <p style={{ color: '#aaa' }}>Закажите перекус к вашему сеансу</p>
        </div>

        {/* Итерация по категориям в заданном порядке. */}
        {CATEGORY_ORDER.map((cat) => {
          const catItems = grouped[cat];
          if (catItems.length === 0) return null; // пустую категорию не рендерим
          const meta = CATEGORY_META[cat];
          return (
            // section — семантический HTML элемент для группы связанного контента.
            <section key={cat} style={{ marginBottom: '2.5rem' }}>
              {/* Заголовок категории с emoji, цветной нижней рамкой. */}
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.2rem' }}>
                <span style={{ fontSize: '1.6rem' }}>{meta.icon}</span>
                {/* borderBottom цвета категории — визуальное разделение секций. */}
                <h2 style={{ fontSize: '1.2rem', fontWeight: '700', borderBottom: `2px solid ${meta.color}`, paddingBottom: '0.3rem', flex: 1 }}>
                  {meta.label}
                </h2>
              </div>

              {/* Сетка карточек товаров: auto-fill minmax(200px, 1fr) — адаптивная. */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '1rem' }}>
                {catItems.map((item) => {
                  const qty = cart[item.id] || 0; // количество этого товара в корзине
                  return (
                    <div
                      key={item.id}
                      style={{
                        background: '#1a1a1a',
                        // Рамка цвета категории если товар в корзине, иначе тёмно-серая.
                        border: `1px solid ${qty > 0 ? meta.color : '#2a2a2a'}`,
                        borderRadius: '12px',
                        padding: '1.2rem',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.5rem',
                        transition: 'border-color 0.2s, transform 0.2s',
                      }}
                      // Hover: карточка чуть поднимается.
                      onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.transform = 'translateY(-2px)'; }}
                      onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.transform = 'translateY(0)'; }}
                    >
                      {/* Большой emoji иконки категории. */}
                      <div style={{ fontSize: '2.2rem', textAlign: 'center', lineHeight: 1 }}>{meta.icon}</div>
                      <h3 style={{ fontSize: '0.95rem', fontWeight: '600', textAlign: 'center', color: '#fff' }}>{item.name}</h3>
                      {/* marginTop:'auto' — цена прижимается к низу карточки. */}
                      <div style={{ marginTop: 'auto', textAlign: 'center', fontSize: '1.2rem', fontWeight: '700', color: meta.color }}>
                        {item.price} ₽
                      </div>
                      {/* Кнопки корзины — только для ROLE_CLIENT. */}
                      {isClient && (
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.6rem', marginTop: '0.3rem' }}>
                          {/* Если товар уже в корзине — показываем счётчик с кнопками + и −. */}
                          {qty > 0 ? (
                            <>
                              <button
                                onClick={() => removeFromCart(item.id)}
                                style={{ background: '#222', border: '1px solid #444', color: '#fff', borderRadius: '6px', width: '28px', height: '28px', fontSize: '1rem', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                              >−</button>
                              <span style={{ fontWeight: '700', fontSize: '1rem', minWidth: '20px', textAlign: 'center' }}>{qty}</span>
                              <button
                                onClick={() => addToCart(item.id)}
                                style={{ background: meta.color, border: 'none', color: '#fff', borderRadius: '6px', width: '28px', height: '28px', fontSize: '1rem', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                              >+</button>
                            </>
                          ) : (
                            /* Товара нет в корзине — показываем кнопку "В корзину". */
                            <button
                              onClick={() => addToCart(item.id)}
                              style={{ background: meta.color, border: 'none', color: '#fff', borderRadius: '6px', padding: '0.4rem 1rem', fontSize: '0.85rem', fontWeight: '600', cursor: 'pointer', width: '100%' }}
                            >
                              В корзину
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </section>
          );
        })}
      </div>

      {/* Правая колонка: сайдбар корзины — только для ROLE_CLIENT. */}
      {isClient && (
        // position:'sticky' + top:'80px' — корзина прикреплена при прокрутке меню.
        // 80px = высота навбара (64px) + небольшой отступ.
        <div style={{ width: '280px', flexShrink: 0, position: 'sticky', top: '80px' }}>
          <div style={{ background: '#1a1a1a', border: '1px solid #2a2a2a', borderRadius: '12px', padding: '1.2rem' }}>
            {/* Заголовок корзины с бейджем количества товаров. */}
            <h3 style={{ fontSize: '1rem', fontWeight: '700', marginBottom: '1rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              🛒 Корзина
              {/* Красный кружок с числом — только если есть товары. */}
              {cartItemCount > 0 && (
                <span style={{ background: '#e50914', color: '#fff', borderRadius: '50%', width: '20px', height: '20px', fontSize: '0.75rem', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
                  {cartItemCount}
                </span>
              )}
            </h3>

            {/* Пустая корзина. */}
            {cartLines.length === 0 ? (
              <div style={{ color: '#555', fontSize: '0.9rem', textAlign: 'center', padding: '1rem 0' }}>
                Корзина пуста
              </div>
            ) : (
              <>
                {/* Список позиций в корзине. */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem', marginBottom: '1rem' }}>
                  {cartLines.map(({ item, qty }) => {
                    const meta = CATEGORY_META[item.category] || { icon: '🍽️', color: '#444' };
                    return (
                      <div key={item.id} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem' }}>
                        <span>{meta.icon}</span>
                        {/* Длинное название обрезается через overflow/ellipsis. */}
                        <span style={{ flex: 1, color: '#ccc', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.name}</span>
                        <span style={{ color: '#888' }}>×{qty}</span>
                        <span style={{ color: '#e50914', fontWeight: '600', minWidth: '55px', textAlign: 'right' }}>
                          {/* toFixed(0) — целая сумма (копейки не нужны для еды). */}
                          {(item.price * qty).toFixed(0)} ₽
                        </span>
                      </div>
                    );
                  })}
                </div>

                {/* Итоговая строка. */}
                <div style={{ borderTop: '1px solid #2a2a2a', paddingTop: '0.8rem', marginBottom: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: '700', fontSize: '1rem' }}>
                    <span>Итого:</span>
                    <span style={{ color: '#e50914' }}>{cartTotal.toFixed(0)} ₽</span>
                  </div>
                </div>

                {/* Ошибка оформления. */}
                {orderError && (
                  <div style={{ color: '#ff6b6b', fontSize: '0.82rem', marginBottom: '0.7rem' }}>{orderError}</div>
                )}

                {/* Успешное оформление — зелёный блок (исчезает через 4 сек). */}
                {orderSuccess && (
                  <div style={{ color: '#4caf50', fontSize: '0.85rem', marginBottom: '0.7rem', background: '#0a1a0a', border: '1px solid #1a4a1a', borderRadius: '6px', padding: '0.5rem 0.7rem' }}>
                    ✓ Заказ оформлен!
                  </div>
                )}

                {/* Кнопка оформления заказа. */}
                <button
                  onClick={placeOrder}
                  disabled={ordering}
                  style={{ width: '100%', background: '#e50914', color: '#fff', border: 'none', borderRadius: '8px', padding: '0.75rem', fontWeight: '700', fontSize: '0.95rem', cursor: ordering ? 'not-allowed' : 'pointer', opacity: ordering ? 0.7 : 1 }}
                >
                  {ordering ? 'Оформление...' : 'Оформить заказ'}
                </button>

                {/* Ссылка для очистки корзины. */}
                <button
                  onClick={() => setCart({})}
                  style={{ width: '100%', background: 'transparent', color: '#666', border: 'none', padding: '0.4rem', fontSize: '0.8rem', cursor: 'pointer', marginTop: '0.4rem' }}
                >
                  Очистить корзину
                </button>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
