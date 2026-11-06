import { useEffect, useState } from 'react';
import { api } from './api.js';
import Icon from './Icons.jsx';

export default function Flashcards({ courseId, admin }) {
  const [cards, setCards] = useState([]);
  const [i, setI] = useState(0);
  const [flip, setFlip] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const load = () => api(`/api/courses/${courseId}/flashcards`).then((c) => { setCards(c); setI(0); setFlip(false); }).catch((e) => setErr(e.message));
  useEffect(() => { load(); }, [courseId]);

  async function generate() {
    setBusy(true); setErr('');
    try { const c = await api(`/api/courses/${courseId}/flashcards/generate`, { method: 'POST' }); setCards(c); setI(0); setFlip(false); }
    catch (e) { setErr(e.message); } finally { setBusy(false); }
  }
  async function clear() {
    if (!window.confirm('Delete all flash cards for this course?')) return;
    try { await api(`/api/courses/${courseId}/flashcards`, { method: 'DELETE' }); setCards([]); } catch (e) { setErr(e.message); }
  }
  const go = (d) => { setFlip(false); setI((x) => (x + d + cards.length) % cards.length); };
  const shuffle = () => { setCards((c) => [...c].sort(() => Math.random() - 0.5)); setI(0); setFlip(false); };

  if (cards.length === 0 && !admin) return null;
  const c = cards[i];

  return (
    <section className="card" style={{ marginTop: '1.5rem' }}>
      <div className="row" style={{ marginBottom: '.5rem' }}>
        <div><span className="eyebrow"><Icon name="card" size={14} /> Revision</span><h2 style={{ margin: 0 }}>Flash cards</h2></div>
        {admin && <div className="row" style={{ margin: 0 }}>
          <button className="ghost" onClick={generate} disabled={busy}><Icon name="sparkles" size={16} /> {busy ? 'Generating…' : cards.length ? 'Regenerate' : 'Generate with AI'}</button>
          {cards.length > 0 && <button className="ghost" onClick={clear}><Icon name="trash" size={15} /></button>}
        </div>}
      </div>
      {err && <p className="error" role="alert">{err}</p>}
      {cards.length === 0 && <p className="empty">No cards yet. Generate them from the lessons.</p>}
      {c && (
        <>
          <button className={`flashcard ${flip ? 'flipped' : ''}`} onClick={() => setFlip(!flip)} aria-label={flip ? 'Showing the answer. Tap to see the question' : 'Showing the question. Tap to see the answer'}>
            <small>{flip ? 'Answer' : 'Question'}{c.lesson ? `, ${c.lesson}` : ''}</small>
            <span>{flip ? c.back : c.front}</span>
          </button>
          <div className="row" style={{ marginTop: '.8rem', marginBottom: 0 }}>
            <button className="ghost" onClick={() => go(-1)}>Previous</button>
            <small>{i + 1} of {cards.length}</small>
            <button className="ghost" onClick={shuffle}><Icon name="repeat" size={15} /> Shuffle</button>
            <button onClick={() => go(1)}>Next</button>
          </div>
        </>
      )}
    </section>
  );
}
