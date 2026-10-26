import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, session } from '../api.js';
import { isAdmin } from '../roles.js';
import { initials } from '../ui.js';
import Icon from '../Icons.jsx';

const blank = { name: '', profession: '', age: '', personality: '', communication: '', knowledgeLevel: '',
  behaviour: '', objections: '', scenario: '', mandatoryInfo: '', prohibitedStatements: '' };
const LEVELS = [[1, 'Guided'], [2, 'Assisted'], [3, 'Independent'], [4, 'Challenging'], [5, 'Expert']];

export default function Simulations() {
  const nav = useNavigate();
  const admin = isAdmin(session.user);
  const [personas, setPersonas] = useState([]);
  const [level, setLevel] = useState(3);
  const [form, setForm] = useState(blank);
  const [showForm, setShowForm] = useState(false);
  const [err, setErr] = useState('');
  const [starting, setStarting] = useState(null);

  const load = () => api('/api/personas').then(setPersonas).catch((e) => setErr(e.message));
  useEffect(() => { load(); }, []);

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
      await api('/api/personas', { method: 'POST', body: { ...form, age: form.age ? Number(form.age) : null } });
      setForm(blank); setShowForm(false); load();
    } catch (ex) { setErr(ex.message); }
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
            <button onClick={() => start(p.id)} disabled={starting === p.id}>{starting === p.id ? 'Starting…' : 'Start simulation'} <Icon name="arrow" size={16} /></button>
          </article>
        ))}
      </div>

      {admin && (
        <section style={{ marginTop: '2.5rem' }}>
          <div className="row"><h2>Create a persona</h2><button className="ghost" onClick={() => setShowForm(!showForm)}><Icon name={showForm ? 'check' : 'plus'} size={16} /> {showForm ? 'Close' : 'New persona'}</button></div>
          {showForm && (
            <form className="card form" onSubmit={savePersona}>
              <label>Name<input required {...f('name')} placeholder="Dr. Raj Mehta" /></label>
              <label>Profession<input required {...f('profession')} placeholder="Cardiologist" /></label>
              <label>Age<input type="number" min="18" max="99" {...f('age')} /></label>
              <label>Personality<input {...f('personality')} placeholder="Sceptical" /></label>
              <label>Communication style<input {...f('communication')} placeholder="Short and direct" /></label>
              <label>Knowledge level<input {...f('knowledgeLevel')} placeholder="Expert" /></label>
              <label className="wide">Behaviour<textarea rows="2" {...f('behaviour')} placeholder="Challenges unsupported claims" /></label>
              <label className="wide">Objections<textarea rows="2" {...f('objections')} placeholder="Price, evidence, efficacy" /></label>
              <label className="wide">Scenario<textarea required rows="3" {...f('scenario')} placeholder="Product detailing. You have two minutes with the doctor." /></label>
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
