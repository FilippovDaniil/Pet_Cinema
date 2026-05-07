import { useState, useEffect, useRef } from 'react';
import api from '../api/axios';
import { SupportTicket, SupportMessage } from '../types';
import { useAuth } from '../context/AuthContext';

export default function SupportPage() {
  const { user } = useAuth();
  const [tickets, setTickets] = useState<SupportTicket[]>([]);
  const [selectedTicket, setSelectedTicket] = useState<SupportTicket | null>(null);
  const [messages, setMessages] = useState<SupportMessage[]>([]);
  const [newSubject, setNewSubject] = useState('');
  const [newMessage, setNewMessage] = useState('');
  const [showNewForm, setShowNewForm] = useState(false);
  const [loading, setLoading] = useState(true);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [error, setError] = useState('');
  const [submitError, setSubmitError] = useState('');
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchTickets();
  }, []);

  useEffect(() => {
    if (selectedTicket) {
      fetchMessages(selectedTicket.id);
      intervalRef.current = setInterval(() => {
        fetchMessages(selectedTicket.id);
      }, 5000);
    }
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [selectedTicket]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

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

  const fetchMessages = async (ticketId: number) => {
    setMessagesLoading(true);
    try {
      const res = await api.get<SupportMessage[]>(`/support/tickets/${ticketId}/messages`);
      setMessages(res.data || []);
    } catch {
    } finally {
      setMessagesLoading(false);
    }
  };

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
      await fetchTickets();
    } catch (e: any) {
      setSubmitError(e.response?.data?.message || 'Ошибка при создании обращения');
    }
  };

  const sendMessage = async () => {
    if (!newMessage.trim() || !selectedTicket) return;
    const content = newMessage;
    setNewMessage('');
    try {
      await api.post(`/support/tickets/${selectedTicket.id}/messages`, { content });
      await fetchMessages(selectedTicket.id);
    } catch {
      setNewMessage(content);
    }
  };

  const statusColors: Record<string, string> = {
    OPEN: '#4caf50',
    CLOSED: '#666',
  };

  const statusLabels: Record<string, string> = {
    OPEN: 'Открыто',
    CLOSED: 'Закрыто',
  };

  return (
    <div>
      <h1 style={{ fontSize: '1.8rem', fontWeight: 'bold', marginBottom: '0.5rem' }}>Поддержка</h1>
      <p style={{ color: '#aaa', marginBottom: '2rem' }}>Свяжитесь с нами, если у вас возникли вопросы</p>

      <div style={{ display: 'flex', gap: '1.5rem', flexWrap: 'wrap' }}>
        {/* Tickets List */}
        <div style={{ flex: '0 0 300px', minWidth: '260px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
            <h2 style={{ fontSize: '1rem', fontWeight: '600' }}>Мои обращения</h2>
            <button
              onClick={() => setShowNewForm(!showNewForm)}
              style={{
                background: '#e50914',
                color: '#fff',
                border: 'none',
                borderRadius: '6px',
                padding: '0.4rem 0.9rem',
                fontSize: '0.85rem',
                fontWeight: '600',
              }}
            >
              + Новое
            </button>
          </div>

          {showNewForm && (
            <div style={{
              background: '#1a1a1a',
              border: '1px solid #333',
              borderRadius: '8px',
              padding: '1rem',
              marginBottom: '1rem',
            }}>
              <input
                type="text"
                value={newSubject}
                onChange={(e) => setNewSubject(e.target.value)}
                placeholder="Тема обращения..."
                onKeyDown={(e) => e.key === 'Enter' && createTicket()}
                style={{
                  width: '100%',
                  background: '#111',
                  border: '1px solid #333',
                  borderRadius: '6px',
                  color: '#fff',
                  padding: '0.6rem 0.8rem',
                  fontSize: '0.9rem',
                  marginBottom: '0.7rem',
                }}
              />
              {submitError && (
                <div style={{ color: '#ff6b6b', fontSize: '0.8rem', marginBottom: '0.5rem' }}>{submitError}</div>
              )}
              <div style={{ display: 'flex', gap: '0.5rem' }}>
                <button
                  onClick={createTicket}
                  style={{
                    flex: 1,
                    background: '#e50914',
                    color: '#fff',
                    border: 'none',
                    borderRadius: '6px',
                    padding: '0.5rem',
                    fontSize: '0.85rem',
                    fontWeight: '600',
                  }}
                >
                  Создать
                </button>
                <button
                  onClick={() => { setShowNewForm(false); setSubmitError(''); }}
                  style={{
                    background: 'transparent',
                    color: '#aaa',
                    border: '1px solid #444',
                    borderRadius: '6px',
                    padding: '0.5rem 0.8rem',
                    fontSize: '0.85rem',
                  }}
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

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
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
                  transition: 'border-color 0.2s',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.3rem' }}>
                  <span style={{ fontWeight: '600', fontSize: '0.9rem', color: '#fff' }}>#{ticket.id}</span>
                  <span style={{
                    fontSize: '0.75rem',
                    fontWeight: '700',
                    color: statusColors[ticket.status] || '#aaa',
                  }}>
                    {statusLabels[ticket.status] || ticket.status}
                  </span>
                </div>
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

        {/* Messages Panel */}
        <div style={{ flex: 1, minWidth: '300px' }}>
          {!selectedTicket ? (
            <div style={{
              height: '400px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              background: '#111',
              borderRadius: '10px',
              border: '1px solid #2a2a2a',
              color: '#555',
              flexDirection: 'column',
              gap: '1rem',
            }}>
              <div style={{ fontSize: '3rem' }}>💬</div>
              <p>Выберите обращение для просмотра сообщений</p>
            </div>
          ) : (
            <div style={{ background: '#111', borderRadius: '10px', border: '1px solid #2a2a2a', overflow: 'hidden' }}>
              {/* Header */}
              <div style={{
                padding: '1rem 1.2rem',
                borderBottom: '1px solid #222',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
              }}>
                <div>
                  <div style={{ fontWeight: '600' }}>#{selectedTicket.id}: {selectedTicket.subject}</div>
                  <div style={{ fontSize: '0.8rem', color: statusColors[selectedTicket.status] || '#aaa', marginTop: '0.2rem' }}>
                    {statusLabels[selectedTicket.status]}
                  </div>
                </div>
                <button
                  onClick={() => setSelectedTicket(null)}
                  style={{ background: 'transparent', border: 'none', color: '#666', fontSize: '1.2rem', cursor: 'pointer' }}
                >
                  ✕
                </button>
              </div>

              {/* Messages */}
              <div style={{ height: '350px', overflowY: 'auto', padding: '1rem' }}>
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
                    const isOwn = msg.senderId === user?.id;
                    return (
                      <div key={msg.id} style={{
                        display: 'flex',
                        justifyContent: isOwn ? 'flex-end' : 'flex-start',
                      }}>
                        <div style={{
                          maxWidth: '70%',
                          background: isOwn ? '#3a0a0a' : '#1a1a1a',
                          border: `1px solid ${isOwn ? '#e50914' : '#2a2a2a'}`,
                          borderRadius: isOwn ? '12px 12px 0 12px' : '12px 12px 12px 0',
                          padding: '0.7rem 1rem',
                        }}>
                          <div style={{ color: '#ddd', fontSize: '0.9rem', lineHeight: '1.5' }}>{msg.content}</div>
                          <div style={{ color: '#666', fontSize: '0.75rem', marginTop: '0.3rem', textAlign: isOwn ? 'right' : 'left' }}>
                            {new Date(msg.sentAt).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                  <div ref={messagesEndRef} />
                </div>
              </div>

              {/* Input */}
              {selectedTicket.status === 'OPEN' && (
                <div style={{ padding: '0.8rem 1rem', borderTop: '1px solid #222', display: 'flex', gap: '0.7rem' }}>
                  <input
                    type="text"
                    value={newMessage}
                    onChange={(e) => setNewMessage(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                    placeholder="Введите сообщение..."
                    style={{
                      flex: 1,
                      background: '#1a1a1a',
                      border: '1px solid #333',
                      borderRadius: '6px',
                      color: '#fff',
                      padding: '0.6rem 0.8rem',
                      fontSize: '0.9rem',
                    }}
                  />
                  <button
                    onClick={sendMessage}
                    disabled={!newMessage.trim()}
                    style={{
                      background: newMessage.trim() ? '#e50914' : '#333',
                      color: '#fff',
                      border: 'none',
                      borderRadius: '6px',
                      padding: '0.6rem 1rem',
                      fontWeight: '600',
                      fontSize: '0.9rem',
                    }}
                  >
                    Отправить
                  </button>
                </div>
              )}
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
