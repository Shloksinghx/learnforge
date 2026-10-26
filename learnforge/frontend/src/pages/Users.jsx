import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { initials, prettyRole } from '../ui.js';
import Icon from '../Icons.jsx';

export default function Users() {
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'LEARNER' });
  const [err, setErr] = useState('');
  const [ok, setOk] = useState('');
  const [busy, setBusy] = useState(false);

  const load = () => api('/api/users').then(setUsers).catch((e) => setErr(e.message));
  useEffect(() => { load(); }, []);

  async function add(e) {
    e.preventDefault(); setErr(''); setOk(''); setBusy(true);
    try {
      await api('/api/users', { method: 'POST', body: form });
      setOk(`${form.fullName} can now sign in with ${form.email}.`);
      setForm({ fullName: '', email: '', password: '', role: 'LEARNER' });
      load();
    } catch (ex) { setErr(ex.message); } finally { setBusy(false); }
  }

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow"><Icon name="users" size={14} /> Admin tool</span><h1>People</h1></div>
      </div>
      <p className="empty">Create accounts for your learners. Share the email and password with them; they can sign in straight away.</p>

      <form className="card form" onSubmit={add} style={{ marginBottom: '2rem' }}>
        <label>Full name<input required value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></label>
        <label>Email<input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
        <label>Temporary password<input type="text" required minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} placeholder="At least 8 characters" /></label>
        <label>Role
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
            <option value="LEARNER">Learner</option>
            <option value="ORG_ADMIN">Administrator</option>
          </select>
        </label>
        <button disabled={busy}><Icon name="plus" size={16} /> Add person</button>
      </form>
      {err && <p className="error" role="alert">{err}</p>}
      {ok && <p className="ok" role="status">{ok}</p>}

      <div className="card table-wrap">
        <table className="table">
          <thead><tr><th>Name</th><th>Email</th><th>Role</th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td><div className="who"><span className="avatar">{initials(u.fullName)}</span>{u.fullName}</div></td>
                <td>{u.email}</td>
                <td><span className="chip">{prettyRole(u.role)}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
        {users.length === 0 && <p className="empty" style={{ padding: '1rem' }}>No people yet.</p>}
      </div>
    </>
  );
}
