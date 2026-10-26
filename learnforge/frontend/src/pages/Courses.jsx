import { useEffect, useMemo, useState } from 'react';
import { api, session } from '../api.js';
import { isAdmin } from '../roles.js';
import Icon from '../Icons.jsx';
import CourseCard from '../CourseCard.jsx';

export default function Courses({ mine = false }) {
  const admin = isAdmin(session.user);
  const [courses, setCourses] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [err, setErr] = useState('');
  const [q, setQ] = useState('');
  const [show, setShow] = useState(false);
  const [form, setForm] = useState({ title: '', category: '', description: '' });

  const load = () => api(`/api/courses?mine=${mine}`).then(setCourses).catch((e) => setErr(e.message)).finally(() => setLoaded(true));
  useEffect(() => { setLoaded(false); load(); }, [mine]);

  const shown = useMemo(() => {
    const t = q.trim().toLowerCase();
    return t ? courses.filter((c) => `${c.title} ${c.category || ''}`.toLowerCase().includes(t)) : courses;
  }, [courses, q]);

  async function create(e) {
    e.preventDefault(); setErr('');
    try { await api('/api/courses', { method: 'POST', body: form }); setForm({ title: '', category: '', description: '' }); setShow(false); load(); }
    catch (ex) { setErr(ex.message); }
  }

  async function enrol(id) {
    setErr('');
    try { await api(`/api/courses/${id}/enrol`, { method: 'POST' }); load(); } catch (e) { setErr(e.message); }
  }

  async function remove(c) {
    if (!window.confirm(`Delete "${c.title}"? Its lessons, questions, enrolments and test results will be removed. This cannot be undone.`)) return;
    setErr('');
    try { await api(`/api/courses/${c.id}`, { method: 'DELETE' }); load(); } catch (e) { setErr(e.message); }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <span className="eyebrow">{mine ? 'Your learning' : 'Catalogue'}</span>
          <h1>{mine ? 'My courses' : 'All courses'}</h1>
        </div>
        <div className="row" style={{ margin: 0 }}>
          <div className="search"><Icon name="search" size={16} /><input aria-label="Search courses" placeholder="Search courses" value={q} onChange={(e) => setQ(e.target.value)} /></div>
          {admin && !mine && <button onClick={() => setShow(!show)}><Icon name={show ? 'check' : 'plus'} size={16} /> {show ? 'Close' : 'New course'}</button>}
        </div>
      </div>
      {err && <p className="error" role="alert">{err}</p>}

      {show && (
        <form className="card form" onSubmit={create} style={{ marginBottom: '1.5rem' }}>
          <label>Title<input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
          <label>Category<input value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} placeholder="Sales, Compliance, Product" /></label>
          <label className="wide">Description<textarea rows="2" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
          <button>Create course</button>
        </form>
      )}

      {loaded && shown.length === 0 && (
        <div className="empty-state">
          <Icon name="book" size={32} />
          <p>{q ? 'No courses match your search.' : mine ? 'You are not enrolled in any courses yet.' : 'No courses yet.'}</p>
        </div>
      )}
      <div className="grid">
        {shown.map((c) => <CourseCard key={c.id} course={c} admin={admin} onEnrol={enrol} onDelete={remove} />)}
      </div>
    </>
  );
}
