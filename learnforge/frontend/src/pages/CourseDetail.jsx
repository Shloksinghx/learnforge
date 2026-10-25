import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, session } from '../api.js';
import { AUTHOR_ROLES } from '../roles.js';

export default function CourseDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const canAuthor = AUTHOR_ROLES.includes(session.user.role);
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

  if (!data) return <p>{err || 'Loading course…'}</p>;
  const { course, lessons } = data;

  return (
    <>
      <p><Link to="/courses">Back to courses</Link></p>
      <small>{course.category || 'Course'}{course.status === 'DRAFT' && ' (draft, not visible to learners)'}</small>
      <h1>{course.title}</h1>
      <p>{course.description}</p>
      {err && <p className="error" role="alert">{err}</p>}

      <div className="row">
        {!course.enrolled && course.status === 'PUBLISHED' && <button onClick={enrol} disabled={busy}>Enrol in this course</button>}
        {course.enrolled && (
          <div className="grow">
            <div className="progress"><i style={{ width: `${course.progress}%` }} /></div>
            <small>{course.progress}% complete</small>
          </div>
        )}
        {canAuthor && course.status === 'DRAFT' && <button onClick={publish} disabled={busy}>Publish course</button>}
        {canAuthor && <Link className="btn ghost-link" to={`/studio/${course.id}`}>AI Question Studio</Link>}
      </div>

      {(course.enrolled || canAuthor) && course.questionCount > 0 && (
        <section className="card">
          <h2>Test your knowledge</h2>
          <p>{course.questionCount} approved questions in the bank. Questions are picked at random and scored automatically.</p>
          <div className="row">
            <label className="inline">Questions
              <select value={count} onChange={(e) => setCount(e.target.value)}>
                {[5, 10, 15, 20, 30].map((n) => <option key={n} value={n}>{n}</option>)}
              </select>
            </label>
            <button onClick={startTest} disabled={busy}>Start test</button>
          </div>
        </section>
      )}

      <section>
        <h2>Lessons</h2>
        {lessons.length === 0 && <p className="empty">No lessons yet.</p>}
        <ol className="lessons">
          {lessons.map((l) => (
            <li key={l.id} className="card">
              <div className="row">
                <button className="link" onClick={() => setOpen(open === l.id ? null : l.id)} aria-expanded={open === l.id}>
                  {l.order}. {l.title} {l.completed && <span className="ok">(done)</span>}
                </button>
                {course.enrolled && !l.completed && <button className="ghost" onClick={() => complete(l.id)} disabled={busy}>Mark complete</button>}
              </div>
              {open === l.id && (l.content
                ? <div className="lesson-body">{l.content}</div>
                : <p className="empty">Enrol to read this lesson.</p>)}
            </li>
          ))}
        </ol>
      </section>

      {canAuthor && (
        <section>
          <h2>Add a lesson</h2>
          <p className="empty">The AI writes test questions only from the lesson text you add here.</p>
          <form className="card form" onSubmit={addLesson}>
            <label className="wide">Title<input required value={lesson.title} onChange={(e) => setLesson({ ...lesson, title: e.target.value })} /></label>
            <label className="wide">Content<textarea required rows="8" value={lesson.content} onChange={(e) => setLesson({ ...lesson, content: e.target.value })} placeholder="Paste the approved training material for this lesson" /></label>
            <button disabled={busy}>Add lesson</button>
          </form>
        </section>
      )}
    </>
  );
}
