import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { initials } from '../ui.js';
import Icon from '../Icons.jsx';

export default function Leaderboard() {
  const [rows, setRows] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [err, setErr] = useState('');
  useEffect(() => { api('/api/leaderboard').then(setRows).catch((e) => setErr(e.message)).finally(() => setLoaded(true)); }, []);
  const medal = ['#fbbf24', '#cbd5e1', '#f59e0b'];

  return (
    <>
      <div className="page-head"><div><span className="eyebrow"><Icon name="trophy" size={14} /> This organisation</span><h1>Leaderboard</h1></div></div>
      <p className="empty">Earn XP by finishing lessons, passing tests and completing simulations.</p>
      {err && <p className="error" role="alert">{err}</p>}
      {loaded && rows.length === 0 && <div className="empty-state"><Icon name="trophy" size={32} /><p>No XP earned yet. Complete a lesson to get on the board.</p></div>}
      <div className="grid-1">
        {rows.map((r) => (
          <div key={r.userId} className={`card result-row ${r.me ? 'me-row' : ''}`}>
            <span className="rank" style={{ background: r.rank <= 3 ? medal[r.rank - 1] : 'var(--surface-2)', color: r.rank <= 3 ? '#2b2100' : 'inherit' }}>{r.rank}</span>
            <span className="avatar">{initials(r.name)}</span>
            <div className="grow"><b>{r.name}{r.me && ' (you)'}</b><br /><small>{r.levelName}</small></div>
            <b className="grad-text">{r.xp} XP</b>
          </div>
        ))}
      </div>
    </>
  );
}
