import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import Icon from '../Icons.jsx';

export default function Marketplace() {
  const nav = useNavigate();
  const [data, setData] = useState(null);
  const [mine, setMine] = useState([]);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

  const load = () => {
    api('/api/marketplace').then(setData).catch((e) => setErr(e.message));
    api('/api/courses').then(setMine).catch(() => {});
  };
  useEffect(() => { load(); }, []);

  async function importCourse(c) {
    setErr(''); setMsg('');
    try {
      const r = await api(`/api/marketplace/courses/${c.id}/import`, { method: 'POST' });
      setMsg(`Imported "${c.title}" as a draft with ${r.questions} questions to review.`);
      nav(`/courses/${r.courseId}`);
    } catch (e) { setErr(e.message); }
  }
  async function importPersona(p) {
    setErr(''); setMsg('');
    try { await api(`/api/marketplace/personas/${p.id}/import`, { method: 'POST' }); setMsg(`Imported ${p.name}. Find them under Simulations.`); } catch (e) { setErr(e.message); }
  }
  async function toggleShare(c) {
    if (!c.shared && !window.confirm(`Share "${c.title}" with other organisations on this platform? They can import a copy of its lessons and questions.`)) return;
    try { await api(`/api/courses/${c.id}/share`, { method: 'POST', body: { shared: !c.shared } }); load(); } catch (e) { setErr(e.message); }
  }

  if (!data) return <p>{err || 'Loading marketplace…'}</p>;
  const publishedMine = mine.filter((c) => c.status === 'PUBLISHED');

  return (
    <>
      <div className="page-head"><div><span className="eyebrow"><Icon name="store" size={14} /> Share and reuse</span><h1>Marketplace</h1></div></div>
      <p className="empty">Courses and personas shared by other organisations. Importing makes an independent draft copy: review everything before you publish it.</p>
      {err && <p className="error" role="alert">{err}</p>}
      {msg && <p className="ok" role="status">{msg}</p>}

      <section>
        <h2>Courses</h2>
        {data.courses.length === 0 && <div className="empty-state"><Icon name="store" size={30} /><p>Nothing has been shared yet.</p></div>}
        <div className="grid">
          {data.courses.map((c) => (
            <article key={c.id} className="card persona-card">
              <span className="chip">{c.category || 'Course'}</span>
              <h3>{c.title}</h3><p>{c.description}</p>
              <small>{c.lessons} lessons, {c.questions} questions. Shared by {c.publisher}</small>
              <button onClick={() => importCourse(c)}><Icon name="download" size={16} /> Import a copy</button>
            </article>
          ))}
        </div>
      </section>

      <section>
        <h2>Personas</h2>
        {data.personas.length === 0 && <div className="empty-state"><p>No personas shared yet.</p></div>}
        <div className="grid">
          {data.personas.map((p) => (
            <article key={p.id} className="card persona-card">
              <h3>{p.name}</h3><small>{p.profession}</small><p>{p.scenario}</p>
              <small>Shared by {p.publisher}</small>
              <button onClick={() => importPersona(p)}><Icon name="download" size={16} /> Import</button>
            </article>
          ))}
        </div>
      </section>

      <section>
        <h2>Share your courses</h2>
        {publishedMine.length === 0 && <div className="empty-state"><p>Publish a course first.</p></div>}
        {publishedMine.map((c) => (
          <div key={c.id} className="card result-row">
            <div className="grow"><b>{c.title}</b><br /><small>{c.shared ? 'Visible to other organisations' : 'Private to your organisation'}</small></div>
            <button className={c.shared ? '' : 'ghost'} onClick={() => toggleShare(c)}>{c.shared ? 'Stop sharing' : 'Share'}</button>
          </div>
        ))}
      </section>
    </>
  );
}
