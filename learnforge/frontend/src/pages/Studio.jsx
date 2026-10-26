import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';
import Icon from '../Icons.jsx';

const L = ['A', 'B', 'C', 'D'];

function Q({ q, busy, onApprove, onDelete }) {
  return (
    <article className="card q">
      <div className="qhead">
        <span className="chip">{q.difficulty.toLowerCase()}</span>
        <span className={`chip ${q.status === 'APPROVED' ? 'chip-glow' : 'chip-draft'}`}>{q.status.toLowerCase()}</span>
      </div>
      <p><b>{q.text}</b></p>
      <ul className="opts">
        {q.options.map((o, i) => (
          <li key={i} className={i === q.correctIndex ? 'correct' : ''}><span className="letter">{L[i]}</span>{o}{i === q.correctIndex && <small style={{ marginLeft: 'auto' }}>correct</small>}</li>
        ))}
      </ul>
      {q.explanation && <p className="empty">{q.explanation}</p>}
      {q.status === 'DRAFT' && (
        <div className="actions">
          <button onClick={() => onApprove(q.id)} disabled={busy}><Icon name="check" size={16} /> Approve</button>
          <button className="ghost" onClick={() => onDelete(q.id)} disabled={busy}><Icon name="trash" size={16} /> Delete</button>
        </div>
      )}
    </article>
  );
}

export default function Studio() {
  const { courseId } = useParams();
  const nav = useNavigate();
  const [courses, setCourses] = useState([]);
  const [qs, setQs] = useState([]);
  const [count, setCount] = useState(10);
  const [difficulty, setDifficulty] = useState('MIXED');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => { api('/api/courses').then(setCourses).catch((e) => setErr(e.message)); }, []);
  const loadQs = () => courseId ? api(`/api/courses/${courseId}/questions`).then(setQs).catch((e) => setErr(e.message)) : setQs([]);
  useEffect(() => { loadQs(); }, [courseId]);

  const run = async (fn) => { setErr(''); setBusy(true); try { await fn(); } catch (e) { setErr(e.message); } finally { setBusy(false); } };
  const generate = () => run(async () => { await api(`/api/courses/${courseId}/questions/generate`, { method: 'POST', body: { count: Number(count), difficulty } }); await loadQs(); });
  const approve = (id) => run(async () => { await api(`/api/questions/${id}/approve`, { method: 'POST' }); await loadQs(); });
  const remove = (id) => run(async () => { await api(`/api/questions/${id}`, { method: 'DELETE' }); await loadQs(); });
  const approveAll = () => run(async () => { await api(`/api/courses/${courseId}/questions/approve-all`, { method: 'POST' }); await loadQs(); });

  const drafts = qs.filter((q) => q.status === 'DRAFT');
  const approved = qs.filter((q) => q.status === 'APPROVED');

  return (
    <>
      <div className="hero">
        <span className="eyebrow"><Icon name="sparkles" size={14} /> Admin tool</span>
        <h1>AI Question <span className="grad-text">Studio</span></h1>
        <p>Pick a course and the AI drafts multiple-choice questions from its lessons. You review each one, then approve. Learners only ever see approved questions.</p>
      </div>
      {err && <p className="error" role="alert">{err}</p>}

      <div className="card form" style={{ marginBottom: '2rem' }}>
        <label className="wide">Course
          <select value={courseId || ''} onChange={(e) => nav(e.target.value ? `/studio/${e.target.value}` : '/studio')}>
            <option value="">Choose a course</option>
            {courses.map((c) => <option key={c.id} value={c.id}>{c.title}{c.status === 'DRAFT' ? ' (draft)' : ''}</option>)}
          </select>
        </label>
        <label>Number of questions
          <select value={count} onChange={(e) => setCount(e.target.value)}>{[5, 10, 15, 20].map((n) => <option key={n}>{n}</option>)}</select>
        </label>
        <label>Difficulty
          <select value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
            <option value="MIXED">Mixed</option><option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option>
          </select>
        </label>
        <button onClick={generate} disabled={busy || !courseId}><Icon name="sparkles" size={16} /> {busy ? 'Working…' : 'Generate with AI'}</button>
      </div>

      {!courseId && <div className="empty-state"><Icon name="sparkles" size={32} /><p>Choose a course above to start.</p></div>}
      {courseId && (
        <>
          <section>
            <div className="row">
              <h2>Needs review ({drafts.length})</h2>
              {drafts.length > 0 && <button className="ghost" onClick={approveAll} disabled={busy}><Icon name="check" size={16} /> Approve all</button>}
            </div>
            {drafts.length === 0 && <div className="empty-state"><p>No drafts. Generate some questions above.</p></div>}
            {drafts.map((q) => <Q key={q.id} q={q} busy={busy} onApprove={approve} onDelete={remove} />)}
          </section>
          <section>
            <h2>Approved question bank ({approved.length})</h2>
            {approved.length === 0 && <div className="empty-state"><p>Nothing approved yet.</p></div>}
            {approved.map((q) => <Q key={q.id} q={q} busy={busy} />)}
          </section>
        </>
      )}
    </>
  );
}
