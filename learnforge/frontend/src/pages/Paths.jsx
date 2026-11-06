import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, session } from '../api.js';
import { isManager } from '../roles.js';
import Icon from '../Icons.jsx';

const TYPES = [['COURSE', 'Learn a course'], ['TEST', 'Pass a knowledge test'], ['EXAM', 'Pass the final exam'], ['SIMULATION', 'Role-play with a persona'], ['SCENARIO', 'Run a scenario']];

export default function Paths() {
  const manager = isManager(session.user);
  const [paths, setPaths] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [courses, setCourses] = useState([]);
  const [personas, setPersonas] = useState([]);
  const [scenarios, setScenarios] = useState([]);
  const [show, setShow] = useState(false);
  const [form, setForm] = useState({ title: '', description: '', level: 'BEGINNER', steps: [] });
  const [step, setStep] = useState({ type: 'COURSE', refId: '' });
  const [goal, setGoal] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const load = () => api('/api/paths').then(setPaths).catch((e) => setErr(e.message)).finally(() => setLoaded(true));
  useEffect(() => {
    load();
    if (manager) {
      api('/api/courses').then(setCourses).catch(() => {});
      api('/api/personas').then(setPersonas).catch(() => {});
      api('/api/scenarios').then(setScenarios).catch(() => {});
    }
  }, []);

  const options = () => (step.type === 'SIMULATION' ? personas.map((p) => [p.id, `${p.name}, ${p.profession}`])
    : step.type === 'SCENARIO' ? scenarios.map((s) => [s.id, s.title])
    : courses.filter((c) => c.status === 'PUBLISHED').map((c) => [c.id, c.title]));
  const labelOf = (s) => {
    const list = s.type === 'SIMULATION' ? personas.map((p) => [p.id, p.name]) : s.type === 'SCENARIO' ? scenarios.map((x) => [x.id, x.title]) : courses.map((c) => [c.id, c.title]);
    return `${TYPES.find((t) => t[0] === s.type)?.[1]}: ${list.find((x) => x[0] === s.refId)?.[1] || 'item'}`;
  };

  function addStep() {
    if (!step.refId) return;
    setForm({ ...form, steps: [...form.steps, { type: step.type, refId: step.refId }] });
    setStep({ ...step, refId: '' });
  }
  const move = (i, d) => {
    const s = [...form.steps]; const j = i + d;
    if (j < 0 || j >= s.length) return;
    [s[i], s[j]] = [s[j], s[i]];
    setForm({ ...form, steps: s });
  };

  async function draft() {
    setBusy(true); setErr('');
    try {
      const d = await api('/api/studio/path-draft', { method: 'POST', body: { goal, level: form.level } });
      setForm({ title: d.title, description: d.description || '', level: d.level, steps: d.steps });
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  async function save(e) {
    e.preventDefault(); setErr('');
    try {
      await api('/api/paths', { method: 'POST', body: form });
      setForm({ title: '', description: '', level: 'BEGINNER', steps: [] }); setShow(false); load();
    } catch (ex) { setErr(ex.message); }
  }

  async function remove(p) {
    if (!window.confirm(`Delete the path "${p.title}"?`)) return;
    try { await api(`/api/paths/${p.id}`, { method: 'DELETE' }); load(); } catch (e) { setErr(e.message); }
  }

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow"><Icon name="route" size={14} /> Guided journeys</span><h1>Learning paths</h1></div>
        {manager && <button onClick={() => setShow(!show)}><Icon name={show ? 'check' : 'plus'} size={16} /> {show ? 'Close' : 'New path'}</button>}
      </div>
      <p className="empty">A path takes a learner step by step: learn, test, practise and certify. Paths marked "For you" match your current level.</p>
      {err && <p className="error" role="alert">{err}</p>}

      {show && manager && (
        <section className="card" style={{ marginBottom: '2rem' }}>
          <span className="eyebrow"><Icon name="sparkles" size={14} /> AI Learning Path Creator</span>
          <div className="form" style={{ marginBottom: '1rem' }}>
            <label className="wide">Goal<input value={goal} maxLength={600} onChange={(e) => setGoal(e.target.value)} placeholder="Get new reps ready to handle sceptical doctors" /></label>
            <button onClick={draft} disabled={busy || goal.trim().length < 5}><Icon name="sparkles" size={16} /> {busy ? 'Drafting…' : 'Draft with AI'}</button>
          </div>
          <form className="form" onSubmit={save}>
            <label>Title<input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
            <label>Level
              <select value={form.level} onChange={(e) => setForm({ ...form, level: e.target.value })}><option value="BEGINNER">Beginner</option><option value="EXPERIENCED">Experienced</option></select>
            </label>
            <label className="wide">Description<textarea rows="2" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
            <div className="wide">
              <b>Steps</b>
              <ol className="steps-edit">
                {form.steps.map((s, i) => (
                  <li key={i}>{labelOf(s)}
                    <span><button type="button" className="ghost icon-btn" onClick={() => move(i, -1)} aria-label="Move up">&uarr;</button>
                      <button type="button" className="ghost icon-btn" onClick={() => move(i, 1)} aria-label="Move down">&darr;</button>
                      <button type="button" className="ghost icon-btn" onClick={() => setForm({ ...form, steps: form.steps.filter((_, k) => k !== i) })} aria-label="Remove step"><Icon name="trash" size={15} /></button></span>
                  </li>
                ))}
              </ol>
              <div className="row" style={{ justifyContent: 'flex-start', marginBottom: 0 }}>
                <select value={step.type} onChange={(e) => setStep({ type: e.target.value, refId: '' })} style={{ width: 'auto' }}>{TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select>
                <select value={step.refId} onChange={(e) => setStep({ ...step, refId: e.target.value })} style={{ width: 'auto', minWidth: 220 }}>
                  <option value="">Choose…</option>{options().map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                </select>
                <button type="button" className="ghost" onClick={addStep} disabled={!step.refId}><Icon name="plus" size={16} /> Add step</button>
              </div>
            </div>
            <button disabled={form.steps.length === 0}>Save path</button>
          </form>
        </section>
      )}

      {loaded && paths.length === 0 && <div className="empty-state"><Icon name="route" size={32} /><p>No learning paths yet.{manager ? ' Create the first one above.' : ''}</p></div>}
      <div className="grid">
        {paths.map((p) => (
          <article key={p.id} className={`card path-card ${p.recommended ? 'recommended' : ''}`}>
            <div className="row" style={{ marginBottom: '.3rem' }}>
              <span className="chip">{p.level.toLowerCase()}</span>
              {p.recommended && <span className="chip chip-glow">For you</span>}
            </div>
            <h3>{p.title}</h3>
            <p className="empty">{p.description}</p>
            <div className="progress-wrap"><div className="progress"><i style={{ width: `${p.progress}%` }} /></div><small>{p.progress}%</small></div>
            <ol className="path-steps">
              {p.steps.map((s) => (
                <li key={s.id} className={s.done ? 'done' : ''}>
                  <span className="dot">{s.done ? <Icon name="check" size={12} /> : ''}</span>
                  <Link to={s.link}>{s.label}</Link>
                </li>
              ))}
            </ol>
            {manager && <button className="ghost" onClick={() => remove(p)}><Icon name="trash" size={15} /> Delete path</button>}
          </article>
        ))}
      </div>
    </>
  );
}
