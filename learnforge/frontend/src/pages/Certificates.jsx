import { useEffect, useState } from 'react';
import { api } from '../api.js';
import Icon from '../Icons.jsx';

const fmt = (d) => new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

export default function Certificates() {
  const [certs, setCerts] = useState([]);
  const [people, setPeople] = useState([]);
  const [form, setForm] = useState({ learnerId: '', title: '', score: 80, validityMonths: 24 });
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');

  const load = () => api('/api/certificates/all').then(setCerts).catch((e) => setErr(e.message));
  useEffect(() => { load(); api('/api/users').then((u) => setPeople(u.filter((x) => x.role === 'LEARNER'))).catch(() => {}); }, []);

  async function issue(e) {
    e.preventDefault(); setErr(''); setMsg('');
    try {
      const c = await api('/api/certificates', { method: 'POST', body: { ...form, score: Number(form.score), validityMonths: Number(form.validityMonths) } });
      setMsg(`Issued ${c.code} to ${c.learnerName}.`); setForm({ ...form, title: '' }); load();
    } catch (ex) { setErr(ex.message); }
  }
  async function revoke(c) {
    if (!window.confirm(`Revoke certificate ${c.code}? It will show as REVOKED when scanned.`)) return;
    try { await api(`/api/certificates/${c.code}/revoke`, { method: 'POST' }); load(); } catch (e) { setErr(e.message); }
  }

  return (
    <>
      <div className="page-head"><div><span className="eyebrow"><Icon name="award" size={14} /> Certification authority</span><h1>Certificates</h1></div></div>
      {err && <p className="error" role="alert">{err}</p>}
      {msg && <p className="ok" role="status">{msg}</p>}

      <form className="card form" onSubmit={issue} style={{ marginBottom: '2rem' }}>
        <label>Learner
          <select required value={form.learnerId} onChange={(e) => setForm({ ...form, learnerId: e.target.value })}>
            <option value="">Choose…</option>{people.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
          </select>
        </label>
        <label>Certification title<input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Certified Sales Professional" /></label>
        <label>Score (%)<input type="number" min="0" max="100" value={form.score} onChange={(e) => setForm({ ...form, score: e.target.value })} /></label>
        <label>Valid for (months)<input type="number" min="1" max="120" value={form.validityMonths} onChange={(e) => setForm({ ...form, validityMonths: e.target.value })} /></label>
        <button><Icon name="plus" size={16} /> Issue certificate</button>
      </form>

      <div className="card table-wrap">
        <table className="table">
          <thead><tr><th>ID</th><th>Learner</th><th>Certification</th><th>Valid until</th><th>Status</th><th /></tr></thead>
          <tbody>{certs.map((c) => (
            <tr key={c.code}>
              <td><a href={`/verify/${c.code}`}>{c.code}</a></td><td>{c.learnerName}</td>
              <td>{c.title.replace('Certificate of Completion: ', '')}</td><td>{fmt(c.validUntil)}</td>
              <td><span className={`chip ${c.status === 'VALID' ? 'chip-glow' : 'chip-draft'}`}>{c.status.toLowerCase()}</span></td>
              <td>{c.status === 'VALID' && <button className="ghost" onClick={() => revoke(c)}>Revoke</button>}</td>
            </tr>
          ))}</tbody>
        </table>
        {certs.length === 0 && <p className="empty" style={{ padding: '1rem' }}>No certificates issued yet.</p>}
      </div>
    </>
  );
}
