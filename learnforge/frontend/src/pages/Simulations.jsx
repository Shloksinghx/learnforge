import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, session } from '../api.js';
import { isAdmin } from '../roles.js';
import { initials } from '../ui.js';
import { PERSONA_LIBRARY } from '../personaLibrary.js';
import Icon from '../Icons.jsx';

const blank = { name: '', profession: '', age: '', personality: '', communication: '', knowledgeLevel: '',
  behaviour: '', objections: '', scenario: '', objective: '', successCriteria: '', expectedBehaviour: '',
  mandatoryInfo: '', prohibitedStatements: '', timeLimitMinutes: '' };
const LEVELS = [[1, 'Guided'], [2, 'Assisted'], [3, 'Independent'], [4, 'Challenging'], [5, 'Expert']];
const industries = [...new Set(PERSONA_LIBRARY.map((p) => p.industry))];

export default function Simulations() {
  const nav = useNavigate();
  const admin = isAdmin(session.user);
  const [personas, setPersonas] = useState([]);
  const [courses, setCourses] = useState([]);
  const [level, setLevel] = useState(3);
  const [form, setForm] = useState(blank);
  const [showForm, setShowForm] = useState(false);
  const [err, setErr] = useState('');
  const [starting, setStarting] = useState(null);
  const [brief, setBrief] = useState('');
  const [briefCourse, setBriefCourse] = useState('');
  const [drafting, setDrafting] = useState(false);

  const load = () => api('/api/personas').then(setPersonas).catch((e) => setErr(e.message));
  useEffect(() => { load(); if (admin) api('/api/courses').then(setCourses).catch(() => {}); }, []);

  async function start(personaId) {
    setErr(''); setStarting(personaId);
    try {
      const sim = await api('/api/simulations', { method: 'POST', body: { personaId, difficulty: Number(level) } });
      nav(`/simulate/${sim.id}`);
    } catch (e) { setErr(e.message); setStarting(null); }
  }

  async function savePersona(e) {
    e.preventDefault(); setErr('');
    try {
      await api('/api/personas', { method: 'POST', body: { ...form, age: form.age ? Number(form.age) : null, timeLimitMinutes: form.timeLimitMinutes ? Number(form.timeLimitMinutes) : null } });
      setForm(blank); setShowForm(false); load();
    } catch (ex) { setErr(ex.message); }
  }

  const fill = (src) => setForm({ ...blank, ...Object.fromEntries(Object.entries(src).filter(([k, v]) => k in blank && v != null).map(([k, v]) => [k, String(v)])) });

  async function draftWithAi() {
    setErr(''); setDrafting(true);
    try {
      const d = await api('/api/studio/persona-draft', { method: 'POST', body: { brief, courseId: briefCourse || null } });
      fill(d); setShowForm(true);
    } catch (e) { setErr(e.message); } finally { setDrafting(false); }
  }

  const f = (k) => ({ value: form[k], onChange: (e) => setForm({ ...form, [k]: e.target.value }) });

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow"><Icon name="mic" size={14} /> Role-play</span><h1>Simulations</h1></div>
        <div className="seg" role="group" aria-label="Difficulty">
          {LEVELS.map(([n, label]) => <button key={n} className={level === n ? 'on' : ''} onClick={() => setLevel(n)} aria-pressed={level === n}>{n} {label}</button>)}
        </div>
      </div>
      <p className="empty">Choose a persona and a difficulty. At level 1 and 2 the persona gives you hints; at level 5 anything can happen.</p>
      {err && <p className="error" role="alert">{err}</p>}

      {personas.length === 0 && <div className="empty-state"><Icon name="chat" size={32} /><p>No personas yet. {admin ? 'Create the first one below.' : 'Ask your administrator to add one.'}</p></div>}
      <div className="grid">
        {personas.map((p) => (
          <article key={p.id} className="card persona-card">
            <div className="persona-top">
              <span className="avatar">{initials(p.name)}</span>
              <div><h3>{p.name}</h3><small>{p.profession}{p.age ? `, ${p.age}` : ''}</small></div>
            </div>
            <p>{p.scenario}</p>
            {p.timeLimitMinutes && <small><Icon name="clock" size={13} /> {p.timeLimitMinutes} minute limit</small>}
            <button onClick={() => start(p.id)} disabled={starting === p.id}>{starting === p.id ? 'Starting…' : 'Start simulation'} <Icon name="arrow" size={16} /></button>
          </article>
        ))}
      </div>

      {admin && (
        <section style={{ marginTop: '2.5rem' }}>
          <div className="row"><h2>Create a persona</h2><button className="ghost" onClick={() => setShowForm(!showForm)}><Icon name={showForm ? 'check' : 'plus'} size={16} /> {showForm ? 'Close' : 'New persona'}</button></div>

          <div className="card" style={{ marginBottom: '1rem' }}>
            <span className="eyebrow"><Icon name="sparkles" size={14} /> AI Persona and Scenario Creator</span>
            <p className="empty">Describe who the learner will meet. The AI drafts the full persona and scenario; you review and edit before saving.</p>
            <div className="form">
              <label className="wide">Brief<textarea rows="2" maxLength={600} value={brief} onChange={(e) => setBrief(e.target.value)} placeholder="A sceptical cardiologist who doubts our new blood-pressure drug" /></label>
              <label>Base it on a course (optional)
                <select value={briefCourse} onChange={(e) => setBriefCourse(e.target.value)}><option value="">None</option>{courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}</select>
              </label>
              <button onClick={draftWithAi} disabled={drafting || brief.trim().length < 5}><Icon name="sparkles" size={16} /> {drafting ? 'Drafting…' : 'Draft with AI'}</button>
            </div>
            <label style={{ marginTop: '1rem', maxWidth: 380 }}>Or start from a ready-made template
              <select value="" onChange={(e) => { const t = PERSONA_LIBRARY[Number(e.target.value)]; if (t) { fill(t); setShowForm(true); } }}>
                <option value="">Choose a template</option>
                {industries.map((ind) => (
                  <optgroup key={ind} label={ind}>
                    {PERSONA_LIBRARY.map((p, i) => p.industry === ind && <option key={i} value={i}>{p.name}, {p.profession}</option>)}
                  </optgroup>
                ))}
              </select>
            </label>
          </div>

          {showForm && (
            <form className="card form" onSubmit={savePersona}>
              <label>Name<input required {...f('name')} placeholder="Dr. Raj Mehta" /></label>
              <label>Profession<input required {...f('profession')} placeholder="Cardiologist" /></label>
              <label>Age<input type="number" min="18" max="99" {...f('age')} /></label>
              <label>Personality<input {...f('personality')} placeholder="Sceptical" /></label>
              <label>Communication style<input {...f('communication')} placeholder="Short and direct" /></label>
              <label>Knowledge level<input {...f('knowledgeLevel')} placeholder="Expert" /></label>
              <label>Time limit (minutes)<input type="number" min="1" max="60" {...f('timeLimitMinutes')} placeholder="No limit" /></label>
              <label className="wide">Behaviour<textarea rows="2" {...f('behaviour')} placeholder="Challenges unsupported claims" /></label>
              <label className="wide">Objections<textarea rows="2" {...f('objections')} placeholder="Price, evidence, efficacy" /></label>
              <label className="wide">Scenario<textarea required rows="3" {...f('scenario')} placeholder="Product detailing. You have two minutes with the doctor." /></label>
              <label className="wide">Objective (what the learner must achieve)<textarea rows="2" {...f('objective')} /></label>
              <label className="wide">Success criteria<textarea rows="2" {...f('successCriteria')} /></label>
              <label className="wide">Expected behaviour (what a good learner does)<textarea rows="2" {...f('expectedBehaviour')} /></label>
              <label className="wide">Information the learner must cover<textarea rows="2" {...f('mandatoryInfo')} /></label>
              <label className="wide">Statements the learner must not make<textarea rows="2" {...f('prohibitedStatements')} /></label>
              <button>Save persona</button>
            </form>
          )}
        </section>
      )}
    </>
  );
}
