import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, session } from '../api.js';
import { isAdmin } from '../roles.js';
import Icon from '../Icons.jsx';
import ScoreRing from '../ScoreRing.jsx';
import CourseCard from '../CourseCard.jsx';

export default function Home() {
  const user = session.user;
  const admin = isAdmin(user);
  const [dash, setDash] = useState(null);
  const [courses, setCourses] = useState([]);
  const [certs, setCerts] = useState([]);
  const [err, setErr] = useState('');

  useEffect(() => {
    Promise.all([api('/api/me/dashboard'), api('/api/courses?mine=true'), api('/api/certificates/mine')])
      .then(([d, c, k]) => { setDash(d); setCourses(c); setCerts(k); })
      .catch((e) => setErr(e.message));
  }, []);

  const cards = dash && [
    ['book', 'Courses enrolled', dash.enrolled],
    ['clock', 'Tests pending', dash.pendingTests],
    ['target', 'Tests taken', dash.testsTaken],
    ['chart', 'Average score', `${dash.averageScore}%`],
  ];

  return (
    <>
      <div className="hero">
        <span className="eyebrow"><Icon name="bolt" size={14} /> Your learning hub</span>
        <h1>Welcome back, <span className="grad-text">{user.fullName.split(' ')[0]}</span></h1>
        <p>{admin ? 'Build courses, let the AI draft test questions, and keep an eye on how your learners are doing.'
                  : 'Pick up where you left off, take a test, or practise a tough conversation with an AI persona.'}</p>
        <div className="actions">
          <Link className="btn" to="/my-courses">Continue learning <Icon name="arrow" size={16} /></Link>
          <Link className="btn ghost" to="/courses">Explore courses</Link>
          {admin && <Link className="btn ghost" to="/studio"><Icon name="sparkles" size={16} /> AI Question Studio</Link>}
        </div>
      </div>
      {err && <p className="error" role="alert">{err}</p>}

      {cards && (
        <div className="stats">
          {cards.map(([icon, label, value]) => (
            <div key={label} className="card stat">
              <span className="stat-icon"><Icon name={icon} size={22} /></span>
              <div><span>{label}</span><b>{value}</b></div>
            </div>
          ))}
        </div>
      )}

      <section>
        <div className="row"><h2>Continue learning</h2><Link to="/my-courses">View all</Link></div>
        {courses.length === 0 && (
          <div className="empty-state"><Icon name="book" size={30} /><p>You are not enrolled in any courses yet.</p><Link className="btn" to="/courses">Browse courses</Link></div>
        )}
        <div className="grid">
          {courses.slice(0, 3).map((c) => <CourseCard key={c.id} course={c} admin={false} />)}
        </div>
      </section>

      <section>
        <h2>Recent test results</h2>
        {(!dash || dash.recent.length === 0) && <div className="empty-state"><Icon name="target" size={30} /><p>Your scores will appear here after your first test.</p></div>}
        {dash?.recent.map((a) => (
          <Link key={a.id} to={`/test/${a.id}`} className="card result-row" style={{ textDecoration: 'none', color: 'inherit' }}>
            {a.status === 'SUBMITTED' ? <ScoreRing value={a.score} size={56} /> : <span className="stat-icon" style={{ background: 'var(--surface-2)' }}><Icon name="clock" /></span>}
            <div className="grow"><b>{a.courseTitle}</b><br /><small>{a.status === 'SUBMITTED' ? `${a.correctCount} of ${a.total} correct` : 'In progress'}</small></div>
            {a.status === 'SUBMITTED' && <span className={a.passed ? 'ok' : 'bad'}>{a.passed ? 'Passed' : 'Not passed'}</span>}
          </Link>
        ))}
      </section>

      <section>
        <h2>My certificates</h2>
        {certs.length === 0 && <div className="empty-state"><Icon name="award" size={30} /><p>Pass a course test to earn your first certificate.</p></div>}
        <div className="grid">
          {certs.map((c) => (
            <a key={c.code} className="card cert-card" href={`/verify/${c.code}`}>
              <span className="cert-seal"><Icon name="award" size={22} /></span>
              <div><b>{c.title.replace('Certificate of Completion: ', '')}</b><br /><small>{c.code} · {c.status.toLowerCase()}</small></div>
            </a>
          ))}
        </div>
      </section>
    </>
  );
}
