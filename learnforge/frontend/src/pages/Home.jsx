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
  const [game, setGame] = useState(null);
  const [courses, setCourses] = useState([]);
  const [certs, setCerts] = useState([]);
  const [insights, setInsights] = useState([]);
  const [coachMsg, setCoachMsg] = useState('');
  const [coachBusy, setCoachBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => {
    Promise.all([api('/api/me/dashboard'), api('/api/courses?mine=true'), api('/api/certificates/mine'), api('/api/me/gamification'), api('/api/me/coach')])
      .then(([d, c, k, g, co]) => { setDash(d); setCourses(c); setCerts(k); setGame(g); setInsights(co.insights); })
      .catch((e) => setErr(e.message));
  }, []);

  async function askCoach() {
    setCoachBusy(true); setErr('');
    try { setCoachMsg((await api('/api/me/coach/ask', { method: 'POST' })).message); }
    catch (e) { setErr(e.message); } finally { setCoachBusy(false); }
  }

  const cards = dash && [
    ['book', 'Courses enrolled', dash.enrolled],
    ['clock', 'Tests pending', dash.pendingTests],
    ['target', 'Tests taken', dash.testsTaken],
    ['chart', 'Average score', `${dash.averageScore}%`],
  ];
  const xpPct = game && game.nextLevelXp ? Math.round(100 * (game.xp - game.levelStartXp) / (game.nextLevelXp - game.levelStartXp)) : 100;

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
          {admin && <Link className="btn ghost" to="/insights"><Icon name="chart" size={16} /> Insights</Link>}
        </div>
      </div>
      {err && <p className="error" role="alert">{err}</p>}

      {!admin && game && (
        <div className="grid two" style={{ marginBottom: '2rem' }}>
          <section className="card level-card">
            <div className="row" style={{ marginBottom: '.5rem' }}>
              <div><span className="eyebrow"><Icon name="trophy" size={14} /> Level {game.level}</span><h2 style={{ margin: 0 }}>{game.levelName}</h2></div>
              <div className="streak"><Icon name="flame" size={20} /> {game.streak}-day streak</div>
            </div>
            <div className="progress-wrap"><div className="progress"><i style={{ width: `${xpPct}%` }} /></div></div>
            <small>{game.xp} XP{game.nextLevelXp ? `, ${game.nextLevelXp - game.xp} XP to the next level` : ', top level reached'}</small>
            <div className="badge-row">
              {game.badges.map((b) => (
                <span key={b.code} className={`badge ${b.earned ? 'on' : ''}`} title={`${b.name}: ${b.description}`}>
                  <Icon name={b.earned ? 'award' : 'shield'} size={16} /> {b.name}
                </span>
              ))}
            </div>
          </section>
          <section className="card">
            <span className="eyebrow"><Icon name="target" size={14} /> Today's challenge</span>
            <h3>{game.todayChallenge || 'All challenges complete!'}</h3>
            {game.challenges.map((c) => (
              <div key={c.title} className="challenge">
                <div className="row" style={{ margin: 0 }}><span className={c.done ? 'ok' : ''}>{c.done ? '✓ ' : ''}{c.title}</span><small>{c.progress}/{c.target}</small></div>
                <div className="progress"><i style={{ width: `${Math.round(100 * c.progress / c.target)}%` }} /></div>
              </div>
            ))}
          </section>
        </div>
      )}

      {!admin && (
        <section className="card coach">
          <div className="row" style={{ marginBottom: '.4rem' }}>
            <div><span className="eyebrow"><Icon name="bulb" size={14} /> Your AI coach</span></div>
            <button className="ghost" onClick={askCoach} disabled={coachBusy}><Icon name="sparkles" size={16} /> {coachBusy ? 'Thinking…' : 'Ask my coach'}</button>
          </div>
          {coachMsg && <p className="coach-msg">{coachMsg}</p>}
          <ul className="insights">{insights.map((t, i) => <li key={i}>{t}</li>)}</ul>
        </section>
      )}

      {cards && (
        <div className="stats" style={{ marginTop: '2rem' }}>
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
          <div className="empty-state"><Icon name="book" size={30} /><p>{admin ? 'Publish a course and learners will see it here.' : 'No courses available yet.'}</p><Link className="btn" to="/courses">Browse courses</Link></div>
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
        {certs.length === 0 && <div className="empty-state"><Icon name="award" size={30} /><p>Pass a course test (and finish its lessons) to earn your first certificate.</p></div>}
        <div className="grid">
          {certs.map((c) => (
            <a key={c.code} className="card cert-card" href={`/verify/${c.code}`}>
              <span className="cert-seal"><Icon name="award" size={22} /></span>
              <div><b>{c.title.replace('Certificate of Completion: ', '')}</b><br /><small>{c.code}, {c.status.toLowerCase()}</small></div>
            </a>
          ))}
        </div>
      </section>
    </>
  );
}
