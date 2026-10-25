import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, session } from '../api.js';

export default function Home() {
  const user = session.user;
  const [dash, setDash] = useState(null);
  const [courses, setCourses] = useState([]);
  const [certs, setCerts] = useState([]);
  const [err, setErr] = useState('');

  useEffect(() => {
    Promise.all([api('/api/me/dashboard'), api('/api/courses?mine=true'), api('/api/certificates/mine')])
      .then(([d, c, k]) => { setDash(d); setCourses(c); setCerts(k); })
      .catch((e) => setErr(e.message));
  }, []);

  return (
    <>
      <h1>Welcome back, {user.fullName.split(' ')[0]}</h1>
      {err && <p className="error" role="alert">{err}</p>}
      {dash && (
        <div className="stats">
          <div className="card stat"><span>Courses enrolled</span><b>{dash.enrolled}</b></div>
          <div className="card stat"><span>Tests pending</span><b>{dash.pendingTests}</b></div>
          <div className="card stat"><span>Tests taken</span><b>{dash.testsTaken}</b></div>
          <div className="card stat"><span>Average score</span><b>{dash.averageScore}%</b></div>
        </div>
      )}

      <section>
        <div className="row"><h2>Continue learning</h2><Link to="/courses" className="btn ghost-link">Explore courses</Link></div>
        {courses.length === 0 && <p className="empty">You are not enrolled in any courses yet.</p>}
        <div className="grid">
          {courses.map((c) => (
            <Link key={c.id} to={`/courses/${c.id}`} className="card course">
              <small>{c.category || 'Course'}</small>
              <h3>{c.title}</h3>
              <div className="progress" role="progressbar" aria-valuenow={c.progress} aria-valuemin="0" aria-valuemax="100"><i style={{ width: `${c.progress}%` }} /></div>
              <small>{c.progress}% complete</small>
            </Link>
          ))}
        </div>
      </section>

      <section>
        <h2>Recent test results</h2>
        {(!dash || dash.recent.length === 0) && <p className="empty">Your scores will appear here after your first test.</p>}
        <ul className="plain">
          {dash?.recent.map((a) => (
            <li key={a.id}>
              <Link to={`/test/${a.id}`}>{a.courseTitle}</Link>{' '}
              {a.status === 'SUBMITTED'
                ? <b className={a.passed ? 'ok' : 'bad'}>{a.score}% {a.passed ? 'passed' : 'not passed'}</b>
                : <small>in progress</small>}
            </li>
          ))}
        </ul>
      </section>

      <section>
        <h2>My certificates</h2>
        {certs.length === 0 && <p className="empty">Certificates you earn will appear here.</p>}
        <ul className="plain">
          {certs.map((c) => (
            <li key={c.code}><a href={`/verify/${c.code}`}>{c.title}</a> <small>{c.code}, {c.status.toLowerCase()}</small></li>
          ))}
        </ul>
      </section>
    </>
  );
}
