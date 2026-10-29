import { useEffect, useState } from 'react';
import { api, download } from '../api.js';
import Icon from '../Icons.jsx';

const fmt = (d) => new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

export default function Insights() {
  const [o, setO] = useState(null);
  const [err, setErr] = useState('');
  useEffect(() => { api('/api/admin/analytics').then(setO).catch((e) => setErr(e.message)); }, []);

  async function exportCsv() {
    setErr('');
    try { await download('/api/admin/export/results.csv', 'results.csv'); } catch (e) { setErr(e.message); }
  }

  if (!o) return <p>{err || 'Loading insights…'}</p>;
  const cards = [
    ['users', 'Learners', o.learners], ['bolt', 'Active this week', o.activeLearners],
    ['book', 'Published courses', o.publishedCourses], ['check', 'Course completion', `${o.completionRate}%`],
    ['chart', 'Average test score', `${o.averageScore}%`], ['target', 'Pass rate', `${o.passRate}%`],
    ['file', 'Tests taken', o.testsTaken], ['award', 'Certificates issued', o.certificatesIssued],
  ];
  const tone = (v) => (v == null ? 'var(--muted)' : v < 65 ? 'var(--bad)' : v < 80 ? 'var(--amber)' : 'var(--ok)');

  return (
    <>
      <div className="page-head">
        <div><span className="eyebrow"><Icon name="chart" size={14} /> Management view</span><h1>Insights</h1></div>
        <button className="ghost" onClick={exportCsv}><Icon name="download" size={16} /> Export results (CSV)</button>
      </div>
      {err && <p className="error" role="alert">{err}</p>}

      <div className="stats">
        {cards.map(([icon, label, value]) => (
          <div key={label} className="card stat"><span className="stat-icon"><Icon name={icon} size={22} /></span><div><span>{label}</span><b>{value}</b></div></div>
        ))}
      </div>

      <section className="card pad-lg">
        <h2>Competency map</h2>
        <p className="empty">Average simulation scores across all learners. Red means the team needs support.</p>
        <div className="bars">
          {o.competencies.map((c) => (
            <div key={c.key} className="bar-row">
              <span>{c.label}</span>
              <div className="track"><i style={{ width: `${c.score ?? 0}%`, background: tone(c.score) }} /></div>
              <b>{c.score == null ? 'n/a' : `${c.score}%`}</b>
            </div>
          ))}
        </div>
      </section>

      <section style={{ marginTop: '2rem' }}>
        <h2>Courses</h2>
        {o.courses.length === 0 && <div className="empty-state"><p>No published courses yet.</p></div>}
        {o.courses.length > 0 && (
          <div className="card table-wrap">
            <table className="table">
              <thead><tr><th>Course</th><th>Enrolled</th><th>Completed</th><th>Tests</th><th>Avg score</th><th>Pass rate</th></tr></thead>
              <tbody>{o.courses.map((c) => (
                <tr key={c.id}><td>{c.title}</td><td>{c.enrolled}</td><td>{c.completed}</td><td>{c.attempts}</td><td>{c.attempts ? `${c.averageScore}%` : '-'}</td><td>{c.attempts ? `${c.passRate}%` : '-'}</td></tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </section>

      <div className="grid two" style={{ marginTop: '2rem' }}>
        <section>
          <h2>Certificates expiring in 30 days</h2>
          {o.expiring.length === 0 && <div className="empty-state"><p>None expiring soon.</p></div>}
          {o.expiring.map((c) => (
            <div key={c.code} className="card result-row"><span className="cert-seal"><Icon name="award" size={20} /></span>
              <div className="grow"><b>{c.learnerName}</b><br /><small>{c.title.replace('Certificate of Completion: ', '')}, expires {fmt(c.validUntil)}</small></div></div>
          ))}
        </section>
        <section>
          <h2>Top learners</h2>
          {o.top.length === 0 && <div className="empty-state"><p>No XP earned yet.</p></div>}
          {o.top.map((r) => (
            <div key={r.userId} className="card result-row"><span className="rank">{r.rank}</span>
              <div className="grow"><b>{r.name}</b><br /><small>{r.levelName}</small></div><b className="grad-text">{r.xp} XP</b></div>
          ))}
        </section>
      </div>
    </>
  );
}
