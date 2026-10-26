import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, session } from '../api.js';
import { isAdmin } from '../roles.js';
import { coverStyle } from '../ui.js';
import Icon from '../Icons.jsx';

export default function CourseDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const admin = isAdmin(session.user);
  const [data, setData] = useState(null);
  const [open, setOpen] = useState(null);
  const [err, setErr] = useState('');
  const [count, setCount] = useState(10);
  const [lesson, setLesson] = useState({ title: '', content: '' });
  const [busy, setBusy] = useState(false);

  const load = () => api(`/api/courses/${id}`).then(setData).catch((e) => setErr(e.message));
  useEffect(() => { load(); }, [id]);

  const run = async (fn) => { setErr(''); setBusy(true); try { await fn(); } catch (e) { setErr(e.message); } finally { setBusy(false); } };
  const enrol = () => run(async () => { await api(`/api/courses/${id}/enrol`, { method: 'POST' }); await load(); });
  const publish = () => run(async () => { await api(`/api/courses/${id}/publish`, { method: 'POST' }); await load(); });
  const complete = (lid) => run(async () => setData(await api(`/api/courses/${id}/lessons/${lid}/complete`, { method: 'POST' })));
  const addLesson = (e) => { e.preventDefault(); run(async () => { setData(await api(`/api/courses/${id}/lessons`, { method: 'POST', body: lesson })); setLesson({ title: '', content: '' }); }); };
  const startTest = () => run(async () => { const a = await api(`/api/courses/${id}/tests/start`, { method: 'POST', body: { count: Number(count) } }); nav(`/test/${a.id}`); });
  const remove = () => {
    if (!window.confirm(`Delete "${data.course.title}"? Lessons, questions, enrolments and results will be removed. This cannot be undone.`)) return;
    run(async () => { await api(`/api/courses/${id}`, { method: 'DELETE' }); nav('/courses'); });
  };

  if (!data) return <p>{err || 'Loading course…'}</p>;
  const { course, lessons } = data;

  return (
    <>
      <p><Link to="/courses">&larr; Back to courses</Link></p>
      <div className="detail-hero" style={coverStyle(course.title)}>
        <span className="chip">{course.category || 'Course'}</span>{' '}
        {course.status === 'DRAFT' && <span className="chip chip-draft">Draft: hidden from learners</span>}
        <h1>{course.title}</h1>
        <p>{course.description}</p>
        <div className="meta" style={{ color: '#e4e8ff' }}>
          <span><Icon name="book" size={14} /> {course.lessonCount} lessons</span>
          <span><Icon name="target" size={14} /> {course.questionCount} test questions</span>
        </div>
        {course.enrolled && (
          <div className="progress-wrap" style={{ marginTop: '1rem', maxWidth: 420 }}>
            <div className="progress"><i style={{ width: `${course.progress}%` }} /></div><b>{course.progress}%</b>
          </div>
        )}
        <div className="row" style={{ marginTop: '1.2rem', marginBottom: 0, justifyContent: 'flex-start' }}>
          {!course.enrolled && course.status === 'PUBLISHED' && !admin && <button onClick={enrol} disabled={busy}>Enrol in this course</button>}
          {admin && course.status === 'DRAFT' && <button onClick={publish} disabled={busy}><Icon name="check" size={16} /> Publish course</button>}
          {admin && <Link className="btn ghost" to={`/studio/${course.id}`}><Icon name="sparkles" size={16} /> AI Question Studio</Link>}
          {admin && <button className="danger" onClick={remove} disabled={busy}><Icon name="trash" size={16} /> Delete course</button>}
        </div>
      </div>
      {err && <p className="error" role="alert">{err}</p>}
      {admin && course.status === 'DRAFT' && <p className="empty">Publishing makes this course visible to every learner in your organisation, and it appears in their My courses automatically.</p>}

      {(course.enrolled || admin) && course.questionCount > 0 && (
        <section className="card test-panel">
          <h2>Test your knowledge</h2>
          <p className="empty">{course.questionCount} approved questions in the bank. Questions are picked at random and scored automatically. Score 60% or more to earn a certificate.</p>
          <div className="row" style={{ marginBottom: 0, justifyContent: 'flex-start' }}>
            <label className="inline">Questions
              <select value={count} onChange={(e) => setCount(e.target.value)}>
                {[5, 10, 15, 20, 30].map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <button onClick={startTest} disabled={busy}><Icon name="bolt" size={16} /> Start test</button>
          </div>
        </section>
      )}

      <section style={{ marginTop: '2rem' }}>
        <h2>Lessons</h2>
        {lessons.length === 0 && <div className="empty-state"><Icon name="book" size={30} /><p>No lessons yet.</p></div>}
        <ol className="lessons">
          {lessons.map((l) => (
            <li key={l.id} className={`card lesson ${l.completed ? 'done' : ''}`}>
              <div className="lesson-top">
                <span className="lesson-no">{l.completed ? <Icon name="check" size={16} /> : l.order}</span>
                <button className="link grow" onClick={() => setOpen(open === l.id ? null : l.id)} aria-expanded={open === l.id}>{l.title}</button>
                {course.enrolled && !l.completed && <button className="ghost" onClick={() => complete(l.id)} disabled={busy}>Mark complete</button>}
              </div>
              {open === l.id && (l.content
                ? <div className="lesson-body">{l.content}</div>
                : <p className="empty">Enrol to read this lesson.</p>)}
            </li>
          ))}
        </ol>
      </section>

      {admin && (
        <section>
          <h2>Add a lesson</h2>
          <p className="empty">The AI writes test questions only from the lesson text you add here.</p>
          <form className="card form" onSubmit={addLesson}>
            <label className="wide">Title<input required value={lesson.title} onChange={(e) => setLesson({ ...lesson, title: e.target.value })} /></label>
            <label className="wide">Content<textarea required rows="8" value={lesson.content} onChange={(e) => setLesson({ ...lesson, content: e.target.value })} placeholder="Paste the approved training material for this lesson" /></label>
            <button disabled={busy}><Icon name="plus" size={16} /> Add lesson</button>
          </form>
        </section>
      )}
    </>
  );
}
