import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api.js';

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

  const Q = ({ q }) => (
    <article className="card q">
      <div className="row"><small>{q.difficulty.toLowerCase()}, {q.status.toLowerCase()}</small></div>
      <p><b>{q.text}</b></p>
      <ol type="A" className="opts">
        {q.options.map((o, i) => <li key={i} className={i === q.correctIndex ? 'correct' : ''}>{o}{i === q.correctIndex && ' (correct)'}</li>)}
      </ol>
      {q.explanation && <p className="empty">{q.explanation}</p>}
      {q.status === 'DRAFT' && (
        <div className="actions">
          <button onClick={() => approve(q.id)} disabled={busy}>Approve</button>
          <button className="ghost" onClick={() => remove(q.id)} disabled={busy}>Delete</button>
        </div>
      )}
    </article>
  );

  return (
    <>
      <h1>AI Question Studio</h1>
      <p className="empty">Pick a course, let the AI draft questions from its lessons, review them, then approve. Learners only ever see approved questions.</p>
      {err && <p className="error" role="alert">{err}</p>}

      <div className="card form">
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
        <button onClick={generate} disabled={busy || !courseId}>{busy ? 'Working…' : 'Generate with AI'}</button>
      </div>

      {courseId && (
        <>
          <section>
            <div className="row">
              <h2>Needs review ({drafts.length})</h2>
              {drafts.length > 0 && <button className="ghost" onClick={approveAll} disabled={busy}>Approve all</button>}
            </div>
            {drafts.length === 0 && <p className="empty">No drafts. Generate some questions above.</p>}
            {drafts.map((q) => <Q key={q.id} q={q} />)}
          </section>
          <section>
            <h2>Approved question bank ({approved.length})</h2>
            {approved.map((q) => <Q key={q.id} q={q} />)}
          </section>
        </>
      )}
    </>
  );
}
