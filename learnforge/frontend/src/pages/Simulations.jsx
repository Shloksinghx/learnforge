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
const blankScenario = { personaId: '', courseId: '', title: '', description: '', objective: '', expectedBehaviour: '',
  mandatoryInfo: '', prohibitedStatements: '', evaluationCriteria: '', successCriteria: '', difficulty: 3, timeLimitMinutes: '' };
const LEVELS = [[1, 'Guided'], [2, 'Assisted'], [3, 'Independent'], [4, 'Challenging'], [5, 'Expert']];
const industries = [...new Set(PERSONA_LIBRARY.map((p) => p.industry))];

const pick = (src, template) => Object.fromEntries(Object.keys(template).map((k) => [k, src[k] == null ? template[k] : String(src[k])]));

export default function Simulations() {
  const nav = useNavigate();
  const admin = isAdmin(session.user);
  const [personas, setPersonas] = useState([]);
  const [scenarios, setScenarios] = useState([]);
  const [courses, setCourses] = useState([]);
  const [level, setLevel] = useState(3);
  const [form, setForm] = useState(blank);
  const [showForm, setShowForm] = useState(false);
  const [sform, setSform] = useState(blankScenario);
  const [showScen, setShowScen] = useState(false);
  const [makePersona, setMakePersona] = useState(null);
  const [err, setErr] = useState('');
  const [starting, setStarting] = useState(null);
  const [brief, setBrief] = useState('');
  const [briefCourse, setBriefCourse] = useState('');
  const [sbrief, setSbrief] = useState('');
  const [drafting, setDrafting] = useState(false);

  const load = () => {
    api('/api/personas').then(setPersonas).catch((e) => setErr(e.message));
    api('/api/scenarios').then(setScenarios).catch(() => {});
  };
  useEffect(() => { load(); if (admin) api('/api/courses').then(setCourses).catch(() => {}); }, []);

  async function start(body, key) {
    setErr(''); setStarting(key);
    try { const sim = await api('/api/simulations', { method: 'POST', body }); nav(`/simulate/${sim.id}`); }
    catch (e) { setErr(e.message); setStarting(null); }
  }

  async function savePersona(e) {
    e.preventDefault(); setErr('');
    try {
      await api('/api/personas', { method: 'POST', body: { ...form, age: form.age ? Number(form.age) : null, timeLimitMinutes: form.timeLimitMinutes ? Number(form.timeLimitMinutes) : null } });
      setForm(blank); setShowForm(false); load();
    } catch (ex) { setErr(ex.message); }
  }

  async function saveScenario(e) {
    e.preventDefault(); setErr('');
    try {
      let personaId = sform.personaId;
      if (!personaId && makePersona) {
        const p = await api('/api/personas', { method: 'POST', body: { ...pick(makePersona, blank), age: makePersona.age ? Number(makePersona.age) : null, timeLimitMinutes: null, scenario: makePersona.scenario || sform.description } });
        personaId = p.id;
      }
      if (!personaId) { setErr('Choose a persona'); return; }
      await api('/api/scenarios', { method: 'POST', body: { ...sform, personaId, courseId: sform.courseId || null, difficulty: Number(sform.difficulty), timeLimitMinutes: sform.timeLimitMinutes ? Number(sform.timeLimitMinutes) : null } });
      setSform(blankScenario); setMakePersona(null); setShowScen(false); load();
    } catch (ex) { setErr(ex.message); }
  }

  async function removeScenario(s) {
    if (!window.confirm(`Delete the scenario "${s.title}"?`)) return;
    try { await api(`/api/scenarios/${s.id}`, { method: 'DELETE' }); load(); } catch (e) { setErr(e.message); }
  }
  async function sharePersona(p) {
    try { await api(`/api/marketplace/personas/${p.id}/share`, { method: 'POST', body: { shared: !p.shared } }); load(); } catch (e) { setErr(e.message); }
  }

  async function draftPersona() {
    setErr(''); setDrafting(true);
    try { const d = await api('/api/studio/persona-draft', { method: 'POST', body: { brief, courseId: briefCourse || null } }); setForm(pick(d, blank)); setShowForm(true); }
    catch (e) { setErr(e.message); } finally { setDrafting(false); }
  }
  async function draftScenario() {
    setErr(''); setDrafting(true);
    try {
      const d = await api('/api/studio/scenario-draft', { method: 'POST', body: { brief: sbrief, courseId: sform.courseId || null } });
      setSform({ ...blankScenario, ...pick(d, blankScenario), courseId: sform.courseId, personaId: '' });
      setMakePersona(d.suggestedPersona ? pick(d.suggestedPersona, blank) : null);
      setShowScen(true);
    } catch (e) { setErr(e.message); } finally { setDrafting(false); }
  }

  const f = (k) => ({ value: form[k], onChange: (e) => setForm({ ...form, [k]: e.target.value }) });
  const sf = (k) => ({ value: sform[k], onChange: (e) => setSform({ ...sform, [k]: e.target.value }) });

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow"><Icon name="mic" size={14} /> Role-play</span><h1>Simulations</h1></div>
        <div className="seg" role="group" aria-label="Difficulty for quick role-play">
          {LEVELS.map(([n, label]) => <button key={n} className={level === n ? 'on' : ''} onClick={() => setLevel(n)} aria-pressed={level === n}>{n} {label}</button>)}
        </div>
      </div>
      <p className="empty">Run a scenario, or role-play quickly with any persona at the difficulty chosen above. Levels 1 and 2 give hints; level 5 is unpredictable.</p>
      {err && <p className="error" role="alert">{err}</p>}

      <section>
        <h2>Scenarios</h2>
        {scenarios.length === 0 && <div className="empty-state"><Icon name="target" size={30} /><p>No scenarios yet.{admin ? ' Create one below.' : ' Your administrator can add some.'}</p></div>}
        <div className="grid">
          {scenarios.map((s) => (
            <article key={s.id} className="card persona-card">
              <div className="persona-top"><span className="avatar">{initials(s.personaName || '?')}</span>
                <div><h3>{s.title}</h3><small>With {s.personaName}, {s.personaProfession}</small></div></div>
              <p>{s.description}</p>
              <small>Difficulty {s.difficulty}{s.timeLimitMinutes ? `, ${s.timeLimitMinutes} minute limit` : ''}</small>
              <div className="card-actions">
                <button onClick={() => start({ scenarioId: s.id }, s.id)} disabled={starting === s.id}>{starting === s.id ? 'Starting…' : 'Start scenario'} <Icon name="arrow" size={16} /></button>
                {admin && <button className="icon-btn danger" aria-label={`Delete ${s.title}`} onClick={() => removeScenario(s)}><Icon name="trash" size={16} /></button>}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h2>Personas: quick role-play</h2>
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
              <div className="card-actions">
                <button onClick={() => start({ personaId: p.id, difficulty: Number(level) }, p.id)} disabled={starting === p.id}>{starting === p.id ? 'Starting…' : 'Start simulation'} <Icon name="arrow" size={16} /></button>
                {admin && <button className="ghost" onClick={() => sharePersona(p)} title="Share with other organisations">{p.shared ? 'Shared' : 'Share'}</button>}
              </div>
            </article>
          ))}
        </div>
      </section>

      {admin && (
        <>
          <section style={{ marginTop: '2.5rem' }}>
            <div className="row"><h2>Create a scenario</h2><button className="ghost" onClick={() => setShowScen(!showScen)}><Icon name={showScen ? 'check' : 'plus'} size={16} /> {showScen ? 'Close' : 'New scenario'}</button></div>
            <div className="card" style={{ marginBottom: '1rem' }}>
              <span className="eyebrow"><Icon name="sparkles" size={14} /> AI Scenario and Simulation Creator</span>
              <p className="empty">For example: "A difficult distributor negotiation based on Module 6". The AI drafts the scenario and suggests a persona; you review before saving.</p>
              <div className="form">
                <label className="wide">Brief<textarea rows="2" maxLength={600} value={sbrief} onChange={(e) => setSbrief(e.target.value)} /></label>
                <label>Base it on a course (optional)
                  <select value={sform.courseId} onChange={(e) => setSform({ ...sform, courseId: e.target.value })}><option value="">None</option>{courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}</select>
                </label>
                <button onClick={draftScenario} disabled={drafting || sbrief.trim().length < 5}><Icon name="sparkles" size={16} /> {drafting ? 'Drafting…' : 'Draft with AI'}</button>
              </div>
            </div>
            {showScen && (
              <form className="card form" onSubmit={saveScenario}>
                <label className="wide">Title<input required {...sf('title')} /></label>
                <label>Persona
                  <select value={sform.personaId} onChange={(e) => setSform({ ...sform, personaId: e.target.value })}>
                    <option value="">{makePersona ? `Create new: ${makePersona.name}` : 'Choose a persona'}</option>
                    {personas.map((p) => <option key={p.id} value={p.id}>{p.name}, {p.profession}</option>)}
                  </select>
                </label>
                <label>Difficulty<select value={sform.difficulty} onChange={(e) => setSform({ ...sform, difficulty: e.target.value })}>{LEVELS.map(([n, l]) => <option key={n} value={n}>{n} {l}</option>)}</select></label>
                <label>Time limit (minutes)<input type="number" min="1" max="60" {...sf('timeLimitMinutes')} placeholder="No limit" /></label>
                <label className="wide">The situation<textarea required rows="3" {...sf('description')} /></label>
                <label className="wide">Objective<textarea rows="2" {...sf('objective')} /></label>
                <label className="wide">Expected behaviour<textarea rows="2" {...sf('expectedBehaviour')} /></label>
                <label className="wide">Mandatory information<textarea rows="2" {...sf('mandatoryInfo')} /></label>
                <label className="wide">Prohibited statements<textarea rows="2" {...sf('prohibitedStatements')} /></label>
                <label className="wide">Evaluation criteria<textarea rows="2" {...sf('evaluationCriteria')} /></label>
                <label className="wide">Success criteria<textarea rows="2" {...sf('successCriteria')} /></label>
                <button>Save scenario</button>
              </form>
            )}
          </section>

          <section style={{ marginTop: '2.5rem' }}>
            <div className="row"><h2>Create a persona</h2><button className="ghost" onClick={() => setShowForm(!showForm)}><Icon name={showForm ? 'check' : 'plus'} size={16} /> {showForm ? 'Close' : 'New persona'}</button></div>
            <div className="card" style={{ marginBottom: '1rem' }}>
              <span className="eyebrow"><Icon name="sparkles" size={14} /> AI Persona Creator</span>
              <p className="empty">Describe who the learner will meet. The AI drafts the persona; you review and edit before saving.</p>
              <div className="form">
                <label className="wide">Brief<textarea rows="2" maxLength={600} value={brief} onChange={(e) => setBrief(e.target.value)} placeholder="A sceptical cardiologist who doubts our new blood-pressure drug" /></label>
                <label>Base it on a course (optional)
                  <select value={briefCourse} onChange={(e) => setBriefCourse(e.target.value)}><option value="">None</option>{courses.map((c) => <option key={c.id} value={c.id}>{c.title}</option>)}</select>
                </label>
                <button onClick={draftPersona} disabled={drafting || brief.trim().length < 5}><Icon name="sparkles" size={16} /> {drafting ? 'Drafting…' : 'Draft with AI'}</button>
              </div>
              <label style={{ marginTop: '1rem', maxWidth: 420 }}>Or start from a ready-made template ({PERSONA_LIBRARY.length} roles)
                <select value="" onChange={(e) => { const t = PERSONA_LIBRARY[Number(e.target.value)]; if (t) { setForm(pick(t, blank)); setShowForm(true); } }}>
                  <option value="">Choose a template</option>
                  {industries.map((ind) => (
                    <optgroup key={ind} label={ind}>
                      {PERSONA_LIBRARY.map((p, i) => p.industry === ind && <option key={i} value={i}>{p.profession}: {p.name}</option>)}
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
                <label className="wide">Behaviour<textarea rows="2" {...f('behaviour')} /></label>
                <label className="wide">Objections<textarea rows="2" {...f('objections')} /></label>
                <label className="wide">Scenario<textarea required rows="3" {...f('scenario')} /></label>
                <label className="wide">Objective<textarea rows="2" {...f('objective')} /></label>
                <label className="wide">Success criteria<textarea rows="2" {...f('successCriteria')} /></label>
                <label className="wide">Expected behaviour<textarea rows="2" {...f('expectedBehaviour')} /></label>
                <label className="wide">Information the learner must cover<textarea rows="2" {...f('mandatoryInfo')} /></label>
                <label className="wide">Statements the learner must not make<textarea rows="2" {...f('prohibitedStatements')} /></label>
                <button>Save persona</button>
              </form>
            )}
          </section>
        </>
      )}
    </>
  );
}
