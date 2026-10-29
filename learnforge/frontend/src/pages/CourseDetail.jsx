import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, upload, session } from '../api.js';
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
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [lesson, setLesson] = useState({ title: '', content: '' });
  const [opts, setOpts] = useState({ mode: 'STANDARD', count: 10, timeLimitMinutes: '', negativeMarking: false, section: 'ALL' });
  const [chat, setChat] = useState([]);
  const [question, setQuestion] = useState('');
  const [asking, setAsking] = useState(false);
  const fileRef = useRef(null);
  const chatEnd = useRef(null);

  const load = () => api(`/api/courses/${id}`).then(setData).catch((e) => setErr(e.message));
  useEffect(() => { load(); }, [id]);
  useEffect(() => { chatEnd.current?.scrollIntoView({ behavior: 'smooth' }); }, [chat.length]);

  const run = async (fn) => { setErr(''); setMsg(''); setBusy(true); try { await fn(); } catch (e) { setErr(e.message); } finally { setBusy(false); } };
  const enrol = () => run(async () => { await api(`/api/courses/${id}/enrol`, { method: 'POST' }); await load(); });
  const publish = () => run(async () => { await api(`/api/courses/${id}/publish`, { method: 'POST' }); await load(); });
  const complete = (lid) => run(async () => setData(await api(`/api/courses/${id}/lessons/${lid}/complete`, { method: 'POST' })));
  const addLesson = (e) => { e.preventDefault(); run(async () => { setData(await api(`/api/courses/${id}/lessons`, { method: 'POST', body: lesson })); setLesson({ title: '', content: '' }); }); };
  const startTest = () => run(async () => {
    const body = { count: Number(opts.count), mode: opts.mode, negativeMarking: opts.negativeMarking, section: opts.section,
      timeLimitMinutes: opts.mode === 'STANDARD' && opts.timeLimitMinutes ? Number(opts.timeLimitMinutes) : null };
    const a = await api(`/api/courses/${id}/tests/start`, { method: 'POST', body });
    nav(`/test/${a.id}`);
  });
  const remove = () => {
    if (!window.confirm(`Delete "${data.course.title}"? Lessons, questions, enrolments and results will be removed. This cannot be undone.`)) return;
    run(async () => { await api(`/api/courses/${id}`, { method: 'DELETE' }); nav('/courses'); });
  };
  const onFile = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    run(async () => {
      const r = await upload(`/api/courses/${id}/content/upload`, file);
      setMsg(`The AI created ${r.created} lessons from ${file.name}: ${r.titles.join(', ')}`);
      await load();
    });
  };

  async function ask(e) {
    e.preventDefault();
    const q = question.trim();
    if (!q || asking) return;
    const history = chat.map((m) => ({ role: m.role, text: m.text }));
    setChat((c) => [...c, { role: 'user', text: q }]);
    setQuestion(''); setAsking(true); setErr('');
    try {
      const r = await api(`/api/courses/${id}/tutor`, { method: 'POST', body: { question: q, history } });
      setChat((c) => [...c, { role: 'model', text: r.answer, sources: r.sources }]);
    } catch (ex) { setErr(ex.message); } finally { setAsking(false); }
  }

  if (!data) return <p>{err || 'Loading course…'}</p>;
  const { course, lessons } = data;
  const canTest = (course.enrolled || admin) && course.questionCount > 0;
  const canTutor = (course.enrolled || admin) && lessons.length > 0;

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
      {msg && <p className="ok" role="status">{msg}</p>}
      {admin && course.status === 'DRAFT' && <p className="empty">Publishing makes this course visible to every learner in your organisation, and it appears in their My courses automatically.</p>}

      {canTest && (
        <section className="card test-panel">
          <h2>Test your knowledge</h2>
          <p className="empty">{course.questionCount} approved questions in the bank. Score 60% or more, and finish all lessons, to earn a certificate.</p>
          <div className="seg" role="group" aria-label="Test type" style={{ marginBottom: '1rem' }}>
            <button className={opts.mode === 'STANDARD' ? 'on' : ''} onClick={() => setOpts({ ...opts, mode: 'STANDARD' })}>Mock test</button>
            <button className={opts.mode === 'ADAPTIVE' ? 'on' : ''} onClick={() => setOpts({ ...opts, mode: 'ADAPTIVE' })}>Adaptive practice</button>
          </div>
          <p className="empty">{opts.mode === 'STANDARD'
            ? 'A full exam: random questions, optional timer and negative marking, then a detailed result analysis.'
            : 'One question at a time. Difficulty rises when you are right and eases when you are wrong, with a mini-lesson after each miss.'}</p>
          <div className="form">
            <label>Questions
              <select value={opts.count} onChange={(e) => setOpts({ ...opts, count: e.target.value })}>{[5, 10, 15, 20, 30, 50].map((n) => <option key={n} value={n}>{n}</option>)}</select>
            </label>
            {opts.mode === 'STANDARD' && (<>
              <label>Section
                <select value={opts.section} onChange={(e) => setOpts({ ...opts, section: e.target.value })}>
                  <option value="ALL">All levels</option><option value="EASY">Easy only</option><option value="MEDIUM">Medium only</option><option value="HARD">Hard only</option>
                </select>
              </label>
              <label>Time limit (minutes)
                <input type="number" min="1" max="180" placeholder="No limit" value={opts.timeLimitMinutes} onChange={(e) => setOpts({ ...opts, timeLimitMinutes: e.target.value })} />
              </label>
            </>)}
            <label className="inline"><input type="checkbox" checked={opts.negativeMarking} onChange={(e) => setOpts({ ...opts, negativeMarking: e.target.checked })} /> Negative marking (wrong answer costs 0.25)</label>
            <button onClick={startTest} disabled={busy}><Icon name="bolt" size={16} /> Start {opts.mode === 'STANDARD' ? 'test' : 'practice'}</button>
          </div>
        </section>
      )}

      {canTutor && (
        <section className="card tutor" style={{ marginTop: '1.5rem' }}>
          <div className="row" style={{ marginBottom: '.5rem' }}>
            <div><span className="eyebrow"><Icon name="bulb" size={14} /> AI Tutor</span><h2 style={{ margin: 0 }}>Ask me anything about this course</h2></div>
          </div>
          <p className="empty">Answers come only from this course's lessons, and I tell you which lessons I used.</p>
          <div className="tutor-chat" aria-live="polite">
            {chat.map((m, i) => (
              <div key={i} className={`msg ${m.role === 'user' ? 'me' : ''}`}>
                <span className="avatar">{m.role === 'user' ? 'You' : 'AI'}</span>
                <div className="bubble">{m.text}
                  {m.sources?.length > 0 && <div className="sources"><Icon name="book" size={13} /> {m.sources.join(', ')}</div>}
                </div>
              </div>
            ))}
            {asking && <div className="msg"><span className="avatar">AI</span><div className="bubble dim">Looking through the lessons…</div></div>}
            <div ref={chatEnd} />
          </div>
          <form className="composer" onSubmit={ask}>
            <input value={question} onChange={(e) => setQuestion(e.target.value)} placeholder="For example: what are the main objections and how do I answer them?" aria-label="Your question" maxLength={1000} />
            <button disabled={asking || !question.trim()}><Icon name="send" size={16} /> Ask</button>
          </form>
        </section>
      )}

      <section style={{ marginTop: '2rem' }}>
        <h2>Lessons</h2>
        {lessons.length === 0 && <div className="empty-state"><Icon name="book" size={30} /><p>No lessons yet.{admin ? ' Upload a document or add a lesson below.' : ''}</p></div>}
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
        <>
          <section className="card upload-card" style={{ marginTop: '2rem' }}>
            <span className="eyebrow"><Icon name="upload" size={14} /> Content engine</span>
            <h2>Upload a document</h2>
            <p className="empty">Upload a PDF, Word, PowerPoint, Excel or text file (up to 15 MB). The AI splits it into short lessons with objectives, key concepts, definitions and FAQs. Review them below; nothing is shown to learners until you publish.</p>
            <input ref={fileRef} type="file" accept=".pdf,.docx,.pptx,.xlsx,.txt,.md,.csv" onChange={onFile} hidden />
            <button onClick={() => fileRef.current?.click()} disabled={busy}><Icon name="upload" size={16} /> {busy ? 'Reading and structuring…' : 'Choose a file'}</button>
          </section>

          <section style={{ marginTop: '2rem' }}>
            <h2>Add a lesson by hand</h2>
            <form className="card form" onSubmit={addLesson}>
              <label className="wide">Title<input required value={lesson.title} onChange={(e) => setLesson({ ...lesson, title: e.target.value })} /></label>
              <label className="wide">Content<textarea required rows="8" value={lesson.content} onChange={(e) => setLesson({ ...lesson, content: e.target.value })} placeholder="Paste the approved training material for this lesson" /></label>
              <button disabled={busy}><Icon name="plus" size={16} /> Add lesson</button>
            </form>
          </section>
        </>
      )}
    </>
  );
}
