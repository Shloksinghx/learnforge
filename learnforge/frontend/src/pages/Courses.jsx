import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, session } from '../api.js';
import { AUTHOR_ROLES } from '../roles.js';

export default function Courses({ mine = false }) {
  const canAuthor = AUTHOR_ROLES.includes(session.user.role);
  const [courses, setCourses] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [err, setErr] = useState('');
  const [show, setShow] = useState(false);
  const [form, setForm] = useState({ title: '', category: '', description: '' });

  const load = () => api(`/api/courses?mine=${mine}`).then(setCourses).catch((e) => setErr(e.message)).finally(() => setLoaded(true));
  useEffect(() => { load(); }, [mine]);

  async function create(e) {
    e.preventDefault(); setErr('');
    try { await api('/api/courses', { method: 'POST', body: form }); setForm({ title: '', category: '', description: '' }); setShow(false); load(); }
    catch (ex) { setErr(ex.message); }
  }

  async function enrol(id) {
    setErr('');
    try { await api(`/api/courses/${id}/enrol`, { method: 'POST' }); load(); } catch (e) { setErr(e.message); }
  }

  return (
    <>
      <div className="row">
        <h1>{mine ? 'My courses' : 'Courses'}</h1>
        {canAuthor && !mine && <button className="ghost" onClick={() => setShow(!show)}>{show ? 'Close' : 'New course'}</button>}
      </div>
      {err && <p className="error" role="alert">{err}</p>}

      {show && (
        <form className="card form" onSubmit={create}>
          <label>Title<input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
          <label>Category<input value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} placeholder="Sales, Compliance, Product" /></label>
          <label className="wide">Description<textarea rows="2" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <button>Create course</button>
        </form>
      )}

      {loaded && courses.length === 0 && (
        <p className="empty">{mine ? 'You are not enrolled in any courses yet.' : 'No courses yet.'}</p>
      )}
      <div className="grid">
        {courses.map((c) => (
          <article key={c.id} className="card course">
            <small>{c.category || 'Course'}{c.status === 'DRAFT' && ' (draft)'}</small>
            <h3><Link to={`/courses/${c.id}`}>{c.title}</Link></h3>
            <p>{c.description}</p>
            <small>{c.lessonCount} lessons, {c.questionCount} test questions</small>
            {c.enrolled && (<><div className="progress"><i style={{ width: `${c.progress}%` }} /></div><small>{c.progress}% complete</small></>)}
            <div className="actions">
              {c.enrolled || canAuthor
                ? <Link className="btn" to={`/courses/${c.id}`}>{c.enrolled ? 'Continue' : 'Open'}</Link>
                : <button onClick={() => enrol(c.id)}>Enrol</button>}
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
