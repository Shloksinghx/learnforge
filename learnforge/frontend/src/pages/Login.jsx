import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, session } from '../api.js';

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
    <div className="login">
      <div className="login-copy">
        <h1>Practise the conversation before it counts.</h1>
        <p>Talk to a sceptical doctor, a reluctant distributor or a difficult customer. Get scored on what you said, then retry.</p>
      </div>
      <form className="card login-form" onSubmit={submit}>
        <h2>{mode === 'login' ? 'Sign in' : 'Create your organisation'}</h2>
        {mode === 'register' && (<>
          <label>Organisation name<input required value={f.orgName} onChange={set('orgName')} /></label>
          <label>Your name<input required value={f.fullName} onChange={set('fullName')} /></label>
        </>)}
        <label>Email<input type="email" required value={f.email} onChange={set('email')} /></label>
        <label>Password<input type="password" required minLength={mode === 'register' ? 8 : 1} value={f.password} onChange={set('password')} /></label>
        {err && <p className="error" role="alert">{err}</p>}
        <button disabled={busy}>{mode === 'login' ? 'Sign in' : 'Create organisation'}</button>
        <button type="button" className="ghost" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? 'Set up a new organisation' : 'I already have an account'}
        </button>
      </form>
    </div>
  );
}
