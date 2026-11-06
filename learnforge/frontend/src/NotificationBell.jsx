import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from './api.js';
import Icon from './Icons.jsx';

export default function NotificationBell() {
  const nav = useNavigate();
  const [feed, setFeed] = useState({ unread: 0, items: [] });
  const [open, setOpen] = useState(false);
  const box = useRef(null);

  const load = () => api('/api/notifications').then(setFeed).catch(() => {});
  useEffect(() => { load(); const t = setInterval(load, 60000); return () => clearInterval(t); }, []);
  useEffect(() => {
    const close = (e) => { if (box.current && !box.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  async function openItem(n) {
    setOpen(false);
    if (!n.read) { await api(`/api/notifications/${n.id}/read`, { method: 'POST' }).catch(() => {}); load(); }
    if (n.link) nav(n.link);
  }
  async function readAll() { await api('/api/notifications/read-all', { method: 'POST' }).catch(() => {}); load(); }

  return (
    <div className="bell" ref={box}>
      <button className="ghost icon-btn" aria-label={`Notifications, ${feed.unread} unread`} onClick={() => { setOpen(!open); if (!open) load(); }}>
        <Icon name="bell" size={18} />{feed.unread > 0 && <span className="bell-dot">{feed.unread > 9 ? '9+' : feed.unread}</span>}
      </button>
      {open && (
        <div className="bell-panel card" role="dialog" aria-label="Notifications">
          <div className="row" style={{ marginBottom: '.4rem' }}><b>Notifications</b>{feed.unread > 0 && <button className="link" onClick={readAll}>Mark all read</button>}</div>
          {feed.items.length === 0 && <p className="empty">Nothing yet.</p>}
          {feed.items.map((n) => (
            <button key={n.id} className={`bell-item ${n.read ? '' : 'unread'}`} onClick={() => openItem(n)}>
              <b>{n.title}</b><small>{n.body}</small>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
