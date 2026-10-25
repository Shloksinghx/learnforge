import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, session } from '../api.js';

const AUTHOR_ROLES = ['ORG_ADMIN', 'LND_MANAGER', 'CONTENT_MANAGER', 'TRAINER'];
const blankPersona = { name: '', profession: '', age: '', personality: '', communication: '', knowledgeLevel: '',
  behaviour: '', objections: '', scenario: '', mandatoryInfo: '', prohibitedStatements: '' };

export default function Simulations() {
  const nav = useNavigate();
  const user = session.user;
  const canAuthor = AUTHOR_ROLES.includes(user.role);
  const [personas, setPersonas] = useState([]);
  const [level, setLevel] = useState(3);
  const [form, setForm] = useState(blankPersona);
  const [showForm, setShowForm] = useState(false);
  const [err, setErr] = useState('');

  const load = () => api('/api/personas').then(setPersonas).catch((e) => setErr(e.message));
  useEffect(() => { load(); }, []);

  async function start(personaId) {
    setErr('');
    try {
      const sim = await api('/api/simulations', { method: 'POST', body: { personaId, difficulty: Number(level) } });
      nav(`/simulate/${sim.id}`);
    } catch (e) { setErr(e.message); }
  }

  async function savePersona(e) {
    e.preventDefault(); setErr('');
    try {
      await api('/api/personas', { method: 'POST', body: { ...form, age: form.age ? Number(form.age) : null } });
      setForm(blankPersona); setShowForm(false); load();
    } catch (ex) { setErr(ex.message); }
  }

  const f = (k) => ({ value: form[k], onChange: (e) => setForm({ ...form, [k]: e.target.value }) });

  return (
    <>
      {err && <p className="error" role="alert">{err}</p>}
      <section>
        <div className="row">
          <h2>Practice a conversation</h2>
          <label className="inline">Difficulty
            <select value={level} onChange={(e) => setLevel(e.target.value)}>
              <option value={1}>1 Guided (hints)</option>
              <option value={2}>2 Assisted</option>
              <option value={3}>3 Independent</option>
              <option value={4}>4 Challenging</option>
              <option value={5}>5 Expert</option>
            </select>
          </label>
        </div>
        {personas.length === 0 && <p className="empty">No personas yet. {canAuthor ? 'Create the first one below.' : 'Ask your trainer to add one.'}</p>}
        <div className="grid">
          {personas.map((p) => (
            <article key={p.id} className="card persona">
              <h3>{p.name}</h3>
              <p className="meta">{p.profession}{p.age ? `, ${p.age}` : ''}</p>
              <p>{p.scenario}</p>
              <button onClick={() => start(p.id)}>Start simulation</button>
            </article>
          ))}
        </div>
      </section>

      {canAuthor && (
        <section>
          <div className="row">
            <h2>Create a persona</h2>
            <button className="ghost" onClick={() => setShowForm(!showForm)}>{showForm ? 'Close' : 'New persona'}</button>
          </div>
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
