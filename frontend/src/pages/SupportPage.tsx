// useState — локальный стейт (тикеты, сообщения, формы).
// useEffect — побочные эффекты: загрузка при монтировании, polling.
// useRef — ссылки на DOM и таймеры (не вызывают ре-рендер при изменении).
import { useState, useEffect, useRef } from 'react';
import api from '../api/axios';
import { SupportTicket, SupportMessage } from '../types';
import { useAuth } from '../context/AuthContext';

// SupportPage — страница поддержки для клиента.
// Интерфейс: список обращений слева + чат выбранного обращения справа.
// Polling: сообщения обновляются каждые 5 секунд (WebSocket не используется).
export default function SupportPage() {
  const { user } = useAuth(); // нужен user.id для определения "свои" сообщения

  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<SupportTicket | null>(null);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [newSubject, setNewSubject] = useState('');    // тема нового обращения
  const [newMessage, setNewMessage] = useState('');    // текст нового сообщения
  const [showNewForm, setShowNewForm] = useState(false); // форма создания обращения
  const [loading, setLoading] = useState(true);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [error, setError] = useState('');
  const [submitError, setSubmitError] = useState(''); // ошибка при создании тикета

  // intervalRef — хранит ID таймера setInterval для polling сообщений.
  // useRef: значение изменяется без ре-рендера; ReturnType<typeof setInterval> = NodeJS.Timeout.
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // messagesEndRef — ссылка на div в конце списка сообщений.
  // Используется для auto-scroll вниз при появлении новых сообщений.
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Загружаем список тикетов при монтировании компонента.
  useEffect(() => {
    fetchTickets();
  }, []);

  // Polling сообщений при смене выбранного тикета.
  // Каждый раз когда пользователь выбирает другой тикет — сбрасываем старый интервал.
  useEffect(() => {
    if (selectedTicket) {
      // Сразу загружаем сообщения.
      fetchMessages(selectedTicket.id);
      // Запускаем polling: каждые 5 сек повторно загружаем сообщения.
      // 5000 мс = 5 секунд — баланс между свежестью и нагрузкой на сервер.
      intervalRef.current = setInterval(() => {
        fetchMessages(selectedTicket.id);
      }, 5000);
    }
    // Cleanup функция: выполняется при смене selectedTicket ИЛИ unmount компонента.
    // Очищаем интервал чтобы избежать утечки памяти и запросов к удалённому компоненту.
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [selectedTicket]);

  // Auto-scroll вниз при добавлении новых сообщений.
  // scrollIntoView с behavior:'smooth' — плавная прокрутка.
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // fetchTickets — загружает список обращений текущего пользователя.
  const fetchTickets = async () => {
    setLoading(true);
    try {
      const res = await api.get<SupportTicket[]>('/support/tickets/my');
      setTickets(res.data || []);
    } catch {
      setError('Не удалось загрузить обращения');
    } finally {
      setLoading(false);
    }
  };

  // fetchMessages — загружает сообщения конкретного тикета.
  // Вызывается вручную при выборе тикета И автоматически polling'ом.
  const fetchMessages = async (ticketId: number) => {
    setMessagesLoading(true);
    try {
      const res = await api.get<SupportMessage[]>(`/support/tickets/${ticketId}/messages`);
      setMessages(res.data || []);
    } catch {
      // Не показываем ошибку при polling — тихо игнорируем.
    } finally {
      setMessagesLoading(false);
    }
  };

  // createTicket — создаёт новое обращение.
  const createTicket = async () => {
    if (!newSubject.trim()) {
      setSubmitError('Введите тему обращения');
      return;
    }
    setSubmitError('');
    try {
      await api.post('/support/tickets', { subject: newSubject });
      setNewSubject('');
      setShowNewForm(false);
      await fetchTickets(); // обновляем список тикетов
    } catch (e: any) {
      setSubmitError(e.response?.data?.message || 'Ошибка при создании обращения');
    }
  };

  // sendMessage — отправляет сообщение в выбранный тикет.
  const sendMessage = async () => {
    if (!newMessage.trim() || !selectedTicket) return;
    const content = newMessage;
    setNewMessage(''); // сразу очищаем поле (оптимистичный UX)
    try {
      await api.post(`/support/tickets/${selectedTicket.id}/messages`, { content });
      await fetchMessages(selectedTicket.id); // обновляем сообщения
    } catch {
      // При ошибке — восстанавливаем текст сообщения.
      setNewMessage(content);
    }
  };

  // Цвета и подписи для статусов тикетов.
  const statusColors: Record<string, string> = {
    OPEN:   '#4caf50', // зелёный — открыто
    CLOSED: '#666',    // серый — закрыто
  };

  const statusLabels: Record<string, string> = {
    OPEN:   'Открыто',
    CLOSED: 'Закрыто',
  };

  return (
    <div>
      <h1 style={{ fontSize: '1.8rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Поддержка</h1>
      <p style={{ color: '#aaa', marginBottom: '2rem' }}>Свяжитесь с нами, если у вас возникли вопросы</p>

      {/* Двухколоночный layout: список тикетов (300px) + чат (flex:1). */}
      <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>

        {/* Левая колонка: список обращений. */}
        <div style={{ flex: '0 0 300px', minWidth: '260px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <h2 style={{ fontSize: '1rem', fontWeight: '600' }}>Мои обращения</h2>
            {/* Кнопка "Новое" — тоглит форму создания. */}
            <button
              onClick={() => setShowNewForm(!showNewForm)}
              style={{ background: '#e50914', color: '#fff', border: 'none', borderRadius: '6px', padding: '0.4rem 0.9rem', fontSize: '0.85rem', fontWeight: '600' }}
            >
              + Новое
            </button>
          </div>

          {/* Форма создания нового обращения. */}
          {showNewForm && (
            <div style={{ background: '#1a1a1a', border: '1px solid #333', borderRadius: '8px', padding: '1rem', marginBottom: '1rem' }}>
              <input
                type="text"
                value={newSubject}
                onChange={(e) => setNewSubject(e.target.value)}
                placeholder="Тема обращения..."
                // Enter — отправить форму (удобство клавиатурной навигации).
                onKeyDown={(e) => e.key === 'Enter' && createTicket()}
                style={{ width: '100%', background: '#111', border: '1px solid #333', borderRadius: '6px', color: '#fff', padding: '0.6rem 0.8rem', fontSize: '0.9rem', marginBottom: '0.7rem' }}
              />
              {submitError && (
                <div style={{ color: '#ff6b6b', fontSize: '0.8rem', marginBottom: '0.5rem' }}>{submitError}</div>
              )}
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button onClick={createTicket} style={{ flex: 1, background: '#e50914', color: '#fff', border: 'none', borderRadius: '6px', padding: '0.5rem', fontSize: '0.85rem', fontWeight: '600' }}>
                  Создать
                </button>
                <button
                  onClick={() => { setShowNewForm(false); setSubmitError(''); }}
                  style={{ background: 'transparent', color: '#aaa', border: '1px solid #444', borderRadius: '6px', padding: '0.5rem 0.8rem', fontSize: '0.85rem' }}
                >
                  Отмена
                </button>
              </div>
            </div>
          )}

          {loading && (
            <div style={{ color: '#aaa', textAlign: 'center', padding: '2rem', fontSize: '0.9rem' }}>
              ⏳ Загрузка...
            </div>
          )}

          {error && (
            <div style={{ color: '#ff6b6b', fontSize: '0.85rem', padding: '0.5rem' }}>{error}</div>
          )}

          {!loading && tickets.length === 0 && (
            <div style={{ color: '#666', textAlign: 'center', padding: '2rem', fontSize: '0.9rem' }}>
              У вас нет обращений
            </div>
          )}

          {/* Список карточек тикетов. */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            {tickets.map((ticket) => (
              <div
                key={ticket.id}
                onClick={() => setSelectedTicket(ticket)}
                style={{
                  // Выделяем выбранный тикет: красная рамка + тёмно-красный фон.
                  background: selectedTicket?.id === ticket.id ? '#2a1a1a' : '#1a1a1a',
                  border: `1px solid ${selectedTicket?.id === ticket.id ? '#e50914' : '#2a2a2a'}`,
                  borderRadius: '8px',
                  padding: '0.8rem 1rem',
                  cursor: 'pointer',
                  transition: 'border-color 0.2s',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.3rem' }}>
                  <span style={{ fontWeight: '600', fontSize: '0.9rem', color: '#fff' }}>#{ticket.id}</span>
                  <span style={{ fontSize: '0.75rem', fontWeight: '700', color: statusColors[ticket.status] || '#aaa' }}>
                    {statusLabels[ticket.status] || ticket.status}
                  </span>
                </div>
                {/* overflow:'hidden' + textOverflow:'ellipsis' + whiteSpace:'nowrap' — обрезаем длинную тему. */}
                <div style={{ color: '#ccc', fontSize: '0.85rem', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {ticket.subject}
                </div>
                <div style={{ color: '#666', fontSize: '0.75rem', marginTop: '0.3rem' }}>
                  {new Date(ticket.createdAt).toLocaleDateString('ru-RU')}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Правая панель: чат выбранного тикета. */}
        <div style={{ flex: 1, minWidth: '300px' }}>
          {/* Placeholder если тикет не выбран. */}
          {!selectedTicket ? (
            <div style={{ height: '400px', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#111', borderRadius: '10px', border: '1px solid #2a2a2a', color: '#555', flexDirection: 'column', gap: '1rem' }}>
              <div style={{ fontSize: '3rem' }}>💬</div>
              <p>Выберите обращение для просмотра сообщений</p>
            </div>
          ) : (
            <div style={{ background: '#111', borderRadius: '10px', border: '1px solid #2a2a2a', overflow: 'hidden' }}>
              {/* Шапка чата: тема тикета + статус + кнопка закрытия. */}
              <div style={{ padding: '1rem 1.2rem', borderBottom: '1px solid #222', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <div style={{ fontWeight: '600' }}>#{selectedTicket.id}: {selectedTicket.subject}</div>
                  <div style={{ fontSize: '0.8rem', color: statusColors[selectedTicket.status] || '#aaa', marginTop: '0.2rem' }}>
                    {statusLabels[selectedTicket.status]}
                  </div>
                </div>
                {/* Кнопка "✕" — деселектируем тикет (скрываем чат). */}
                <button
                  onClick={() => setSelectedTicket(null)}
                  style={{ background: 'transparent', border: 'none', color: '#666', fontSize: '1.2rem', cursor: 'pointer' }}
                >
                  ✕
                </button>
              </div>

              {/* Область сообщений: height:350px + overflowY:'auto' — скроллируемый контейнер. */}
              <div style={{ height: '350px', overflowY: 'auto', padding: '1rem' }}>
                {/* Индикатор загрузки сообщений (только если список пустой). */}
                {messagesLoading && messages.length === 0 && (
                  <div style={{ textAlign: 'center', color: '#aaa', padding: '2rem' }}>⏳ Загрузка...</div>
                )}
                {messages.length === 0 && !messagesLoading && (
                  <div style={{ textAlign: 'center', color: '#666', padding: '2rem' }}>
                    Сообщений пока нет
                  </div>
                )}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
                  {messages.map((msg) => {
                    // isOwn: true если senderId совпадает с ID текущего пользователя.
                    // Собственные сообщения — справа; чужие — слева.
                    const isOwn = msg.senderId === user?.id;
                    return (
                      <div key={msg.id} style={{ display: 'flex', justifyContent: isOwn ? 'flex-end' : 'flex-start' }}>
                        <div style={{
                          maxWidth: '70%',
                          // Тёмно-красный фон для собственных, тёмно-серый для чужих.
                          background: isOwn ? '#3a0a0a' : '#1a1a1a',
                          border: `1px solid ${isOwn ? '#e50914' : '#2a2a2a'}`,
                          // borderRadius: "хвост" пузыря направлен в сторону отправителя.
                          borderRadius: isOwn ? '12px 12px 0 12px' : '12px 12px 12px 0',
                          padding: '0.7rem 1rem',
                        }}>
                          <div style={{ color: '#ddd', fontSize: '0.9rem', lineHeight: '1.5' }}>{msg.content}</div>
                          {/* Время — выравнивается по стороне отправителя. */}
                          <div style={{ color: '#666', fontSize: '0.75rem', marginTop: '0.3rem', textAlign: isOwn ? 'right' : 'left' }}>
                            {new Date(msg.sentAt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                  {/* Пустой div в конце — scrollIntoView прокручивает к нему. */}
                  <div ref={messagesEndRef} />
                </div>
              </div>

              {/* Поле ввода — только для открытых тикетов. */}
              {selectedTicket.status === 'OPEN' && (
                <div style={{ padding: '0.8rem 1rem', borderTop: '1px solid #222', display: 'flex', gap: '0.7rem' }}>
                  <input
                    type="text"
                    value={newMessage}
                    onChange={(e) => setNewMessage(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && sendMessage()} // Enter = отправить
                    placeholder="Введите сообщение..."
                    style={{ flex: 1, background: '#1a1a1a', border: '1px solid #333', borderRadius: '6px', color: '#fff', padding: '0.6rem 0.8rem', fontSize: '0.9rem' }}
                  />
                  <button
                    onClick={sendMessage}
                    disabled={!newMessage.trim()} // заблокировать если поле пустое
                    style={{ background: newMessage.trim() ? '#e50914' : '#333', color: '#fff', border: 'none', borderRadius: '6px', padding: '0.6rem 1rem', fontWeight: '600', fontSize: '0.9rem' }}
                  >
                    Отправить
                  </button>
                </div>
              )}
              {/* Уведомление для закрытых тикетов — поле ввода не показываем. */}
              {selectedTicket.status === 'CLOSED' && (
                <div style={{ padding: '0.8rem 1rem', borderTop: '1px solid #222', color: '#666', fontSize: '0.85rem', textAlign: 'center' }}>
                  Обращение закрыто
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
