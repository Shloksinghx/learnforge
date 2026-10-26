import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, session } from '../api.js';
import Icon from '../Icons.jsx';
import ScoreRing from '../ScoreRing.jsx';

export default function Login() {
  const nav = useNavigate();
  const [mode, setMode] = useState('login');
  const [f, setF] = useState({ orgName: '', fullName: '', email: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setErr(''); setBusy(true);
    try {
      const res = mode === 'login'
        ? await api('/api/auth/login', { method: 'POST', body: { email: f.email, password: f.password } })
        : await api('/api/auth/register-org', { method: 'POST', body: f });
      session.set(res);
      nav('/');
    } catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }

  return (
    <div className="auth">
      <section className="auth-hero">
        <span className="chip chip-glow"><Icon name="sparkles" size={14} /> AI-powered learning</span>
        <h1 style={{ marginTop: '1rem' }}>Practise the conversation <span className="grad-text">before it counts.</span></h1>
        <p>Learn from your organisation's own material, talk to realistic AI personas, take scored tests and earn certificates you can prove.</p>
        <ul className="feature-list">
          <li><span className="stat-icon"><Icon name="book" /></span> Courses and lessons built from approved content</li>
          <li><span className="stat-icon"><Icon name="mic" /></span> Voice and text role-play with AI personas</li>
          <li><span className="stat-icon"><Icon name="award" /></span> Automatic scoring and verifiable certificates</li>
        </ul>
        <div className="mock" aria-hidden="true">
          <div className="msg"><span className="avatar">AI</span><div className="bubble">You have two minutes. Why should I consider your product?</div></div>
          <div className="msg me"><span className="avatar">You</span><div className="bubble">Because the trial data shows a clear benefit for your patients.</div></div>
          <div className="card mock-score"><ScoreRing value={86} size={64} /><div><b>Great objection handling</b><br /><small>Product knowledge 86%</small></div></div>
        </div>
      </section>

      <form className="card auth-card" onSubmit={submit}>
        <h2>{mode === 'login' ? 'Welcome back' : 'Create your organisation'}</h2>
        {mode === 'register' && (<>
          <label>Organisation name<input required value={f.orgName} onChange={set('orgName')} /></label>
          <label>Your name<input required value={f.fullName} onChange={set('fullName')} /></label>
        </>)}
        <label>Email<input type="email" required value={f.email} onChange={set('email')} /></label>
        <label>Password<input type="password" required minLength={mode === 'register' ? 8 : 1} value={f.password} onChange={set('password')} /></label>
        {mode === 'register' && <small>This creates a separate organisation and makes you its administrator. Learners should ask their administrator to create their account instead.</small>}
        {err && <p className="error" role="alert">{err}</p>}
        <button disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create organisation'}</button>
        <button type="button" className="ghost" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setErr(''); }}>
          {mode === 'login' ? 'Set up a new organisation' : 'I already have an account'}
        </button>
      </form>
    </div>
  );
}
