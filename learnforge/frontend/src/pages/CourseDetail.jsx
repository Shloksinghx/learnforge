import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, upload, session } from '../api.js';
import { isAdmin } from '../roles.js';
import { coverStyle } from '../ui.js';
import Icon from '../Icons.jsx';
import Flashcards from '../Flashcards.jsx';
import VoiceAssessment from '../VoiceAssessment.jsx';

/** Turns a YouTube, Vimeo or direct video link into something we can embed. */
function Video({ url }) {
  let m = url.match(/(?:youtube\.com\/watch\?v=|youtu\.be\/)([\w-]{6,})/);
  if (m) return <iframe className="video" src={`https://www.youtube.com/embed/${m[1]}`} title="Lesson video" allowFullScreen />;
  m = url.match(/vimeo\.com\/(\d+)/);
  if (m) return <iframe className="video" src={`https://player.vimeo.com/video/${m[1]}`} title="Lesson video" allowFullScreen />;
  if (/\.(mp4|webm|ogg)(\?.*)?$/i.test(url)) return <video className="video" src={url} controls />;
  return <p><a href={url} target="_blank" rel="noreferrer"><Icon name="video" size={15} /> Open the video</a></p>;
}

export default function CourseDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const admin = isAdmin(session.user);
  const [data, setData] = useState(null);
  const [open, setOpen] = useState(null);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [lesson, setLesson] = useState({ title: '', content: '', moduleTitle: '', videoUrl: '' });
  const [opts, setOpts] = useState({ mode: 'STANDARD', count: 10, timeLimitMinutes: '', negativeMarking: false, section: 'ALL' });
  const [cert, setCert] = useState(null);
  const [mocks, setMocks] = useState([]);
  const [mock, setMock] = useState({ title: '', questionCount: 20, minutes: 30, easyPct: 30, mediumPct: 50, hardPct: 20, negativeMarking: false });
  const [docs, setDocs] = useState([]);
  const [url, setUrl] = useState('');
  const [settings, setSettings] = useState(null);
  const [chat, setChat] = useState([]);
  const [question, setQuestion] = useState('');
  const [asking, setAsking] = useState(false);
  const fileRef = useRef(null);
  const chatEnd = useRef(null);

  const load = () => api(`/api/courses/${id}`).then((d) => {
    setData(d);
    setSettings((s) => s || { certificateTitle: d.course.certificateTitle || '', passMark: d.course.passMark, examCount: d.course.examCount, examMinutes: d.course.examMinutes, requiresSimulation: d.course.requiresSimulation });
  }).catch((e) => setErr(e.message));
  const loadExtras = () => {
    api(`/api/courses/${id}/certification`).then(setCert).catch(() => {});
    api(`/api/courses/${id}/mocks`).then(setMocks).catch(() => {});
    if (admin) api(`/api/courses/${id}/documents`).then(setDocs).catch(() => {});
  };
  useEffect(() => { load(); loadExtras(); }, [id]);
  useEffect(() => { chatEnd.current?.scrollIntoView({ behavior: 'smooth' }); }, [chat.length]);

  const run = async (fn) => { setErr(''); setMsg(''); setBusy(true); try { await fn(); } catch (e) { setErr(e.message); } finally { setBusy(false); } };
  const enrol = () => run(async () => { await api(`/api/courses/${id}/enrol`, { method: 'POST' }); await load(); });
  const publish = () => run(async () => { await api(`/api/courses/${id}/publish`, { method: 'POST' }); await load(); });
  const complete = (lid) => run(async () => { setData(await api(`/api/courses/${id}/lessons/${lid}/complete`, { method: 'POST' })); loadExtras(); });
  const addLesson = (e) => {
    e.preventDefault();
    run(async () => {
      setData(await api(`/api/courses/${id}/lessons`, { method: 'POST', body: { ...lesson, moduleTitle: lesson.moduleTitle || null, videoUrl: lesson.videoUrl || null } }));
      setLesson({ title: '', content: '', moduleTitle: '', videoUrl: '' });
    });
  };
  const startTest = () => run(async () => {
    const body = { count: Number(opts.count), mode: opts.mode, negativeMarking: opts.negativeMarking, section: opts.section,
      timeLimitMinutes: opts.mode === 'STANDARD' && opts.timeLimitMinutes ? Number(opts.timeLimitMinutes) : null };
    nav(`/test/${(await api(`/api/courses/${id}/tests/start`, { method: 'POST', body })).id}`);
  });
  const startExam = () => run(async () => nav(`/test/${(await api(`/api/courses/${id}/exam/start`, { method: 'POST' })).id}`));
  const startMock = (m) => run(async () => nav(`/test/${(await api(`/api/mocks/${m.id}/start`, { method: 'POST' })).id}`));
  const remove = () => {
    if (!window.confirm(`Delete "${data.course.title}"? Lessons, questions, enrolments and results will be removed. This cannot be undone.`)) return;
    run(async () => { await api(`/api/courses/${id}`, { method: 'DELETE' }); nav('/courses'); });
  };
  const createMock = (e) => {
    e.preventDefault();
    run(async () => { await api(`/api/courses/${id}/mocks`, { method: 'POST', body: mock }); setMock({ ...mock, title: '' }); loadExtras(); });
  };
  const removeMock = (m) => run(async () => { await api(`/api/mocks/${m.id}`, { method: 'DELETE' }); loadExtras(); });
  const fillMock = (m) => run(async () => { const r = await api(`/api/mocks/${m.id}/generate-missing`, { method: 'POST' }); setMsg(r.message); });
  const saveSettings = (e) => {
    e.preventDefault();
    run(async () => {
      await api(`/api/courses/${id}/settings`, { method: 'PUT', body: { ...settings, passMark: Number(settings.passMark), examCount: Number(settings.examCount), examMinutes: Number(settings.examMinutes) } });
      setMsg('Certification settings saved.'); await load(); loadExtras();
    });
  };
  const share = () => run(async () => { await api(`/api/courses/${id}/share`, { method: 'POST', body: { shared: !data.course.shared } }); await load(); });
  const onFile = (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    run(async () => {
      const r = await upload(`/api/courses/${id}/content/upload`, file);
      setMsg(`The AI created ${r.created} lessons from ${file.name}: ${r.titles.join(', ')}`);
      await load(); loadExtras();
    });
  };
  const fromUrl = (e) => {
    e.preventDefault();
    run(async () => {
      const r = await api(`/api/courses/${id}/content/url`, { method: 'POST', body: { url } });
      setMsg(`The AI created ${r.created} lessons from that page.`); setUrl(''); await load(); loadExtras();
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
  const learns = course.enrolled || admin;
  const canTest = learns && course.questionCount > 0;
  const canTutor = learns && lessons.length > 0;

  // group lessons by module, keeping their order
  const groups = [];
  lessons.forEach((l) => {
    const key = l.moduleTitle || '';
    const last = groups[groups.length - 1];
    if (last && last.key === key) last.items.push(l); else groups.push({ key, items: [l] });
  });

  return (
    <>
      <p><Link to="/courses">&larr; Back to courses</Link></p>
      <div className="detail-hero" style={coverStyle(course.title)}>
        <span className="chip">{course.category || 'Course'}</span>{' '}
        {course.status === 'DRAFT' && <span className="chip chip-draft">Draft: hidden from learners</span>}
        {course.shared && <span className="chip chip-glow">Shared on marketplace</span>}
        <h1>{course.title}</h1>
        <p>{course.description}</p>
        <div className="meta" style={{ color: 'inherit' }}>
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
          {admin && course.status === 'PUBLISHED' && <button className="ghost" onClick={share} disabled={busy}><Icon name="store" size={16} /> {course.shared ? 'Stop sharing' : 'Share'}</button>}
          {admin && <button className="danger" onClick={remove} disabled={busy}><Icon name="trash" size={16} /> Delete course</button>}
        </div>
      </div>
      {err && <p className="error" role="alert">{err}</p>}
      {msg && <p className="ok" role="status">{msg}</p>}
      {admin && course.status === 'DRAFT' && <p className="empty">Publishing makes this course visible to every learner in your organisation, and it appears in their My courses automatically.</p>}

      {learns && cert && (
        <section className="card cert-panel">
          <span className="eyebrow"><Icon name="award" size={14} /> Certification</span>
          <h2>{cert.certified ? 'You are certified' : 'Earn your certificate'}</h2>
          <ol className="cert-steps">
            {cert.steps.map((s) => (
              <li key={s.key} className={s.done ? 'done' : ''}>
                <span className="dot">{s.done ? <Icon name="check" size={12} /> : ''}</span>
                <div><b>{s.label}</b><br /><small>{s.hint}</small></div>
              </li>
            ))}
          </ol>
          <div className="row" style={{ justifyContent: 'flex-start', marginBottom: 0 }}>
            {cert.certified
              ? <a className="btn" href={`/verify/${cert.certificateCode}`}><Icon name="award" size={16} /> View certificate</a>
              : <button onClick={startExam} disabled={busy || (!cert.eligibleForExam && !admin) || course.questionCount === 0}><Icon name="bolt" size={16} /> Start the final exam</button>}
            {!cert.eligibleForExam && !admin && !cert.certified && <small>Finish the steps above to unlock the exam.</small>}
          </div>
        </section>
      )}

      {canTest && (
        <section className="card test-panel" style={{ marginTop: '1.5rem' }}>
          <h2>Practise and test</h2>
          <div className="seg" role="group" aria-label="Test type" style={{ marginBottom: '1rem' }}>
            <button className={opts.mode === 'STANDARD' ? 'on' : ''} onClick={() => setOpts({ ...opts, mode: 'STANDARD' })}>Mock test</button>
            <button className={opts.mode === 'ADAPTIVE' ? 'on' : ''} onClick={() => setOpts({ ...opts, mode: 'ADAPTIVE' })}>Adaptive practice</button>
          </div>
          <p className="empty">{opts.mode === 'STANDARD'
            ? 'A test with random questions, an optional timer and negative marking, then a detailed result analysis.'
            : 'One question at a time. Difficulty rises when you are right and eases when you are wrong, with a mini-lesson after each miss.'}</p>
          <div className="form">
            <label>Questions
              <select value={opts.count} onChange={(e) => setOpts({ ...opts, count: e.target.value })}>{[5, 10, 15, 20, 30, 50, 100, 200].map((n) => <option key={n} value={n}>{n}</option>)}</select>
            </label>
            {opts.mode === 'STANDARD' && (<>
              <label>Section
                <select value={opts.section} onChange={(e) => setOpts({ ...opts, section: e.target.value })}>
                  <option value="ALL">All levels</option><option value="EASY">Easy only</option><option value="MEDIUM">Medium only</option><option value="HARD">Hard only</option>
                </select>
              </label>
              <label>Time limit (minutes)
                <input type="number" min="1" max="240" placeholder="No limit" value={opts.timeLimitMinutes} onChange={(e) => setOpts({ ...opts, timeLimitMinutes: e.target.value })} />
              </label>
            </>)}
            <label className="inline"><input type="checkbox" checked={opts.negativeMarking} onChange={(e) => setOpts({ ...opts, negativeMarking: e.target.checked })} /> Negative marking (wrong answer costs 0.25)</label>
            <button onClick={startTest} disabled={busy}><Icon name="bolt" size={16} /> Start {opts.mode === 'STANDARD' ? 'test' : 'practice'}</button>
          </div>
        </section>
      )}

      {learns && (mocks.length > 0 || admin) && (
        <section className="card" style={{ marginTop: '1.5rem' }}>
          <span className="eyebrow"><Icon name="file" size={14} /> Mock tests</span>
          <h2>Full mock examinations</h2>
          {mocks.length === 0 && <p className="empty">No mock tests yet.</p>}
          {mocks.map((m) => (
            <div key={m.id} className="result-row" style={{ padding: '.6rem 0', borderTop: '1px solid var(--line)' }}>
              <div className="grow"><b>{m.title}</b><br /><small>{m.questionCount} questions, {m.minutes} minutes, {m.easyPct}/{m.mediumPct}/{m.hardPct}% easy/medium/hard{m.negativeMarking ? ', negative marking' : ''}
                {m.shortage > 0 && ` · only ${m.available} approved questions, ${m.shortage} short`}</small></div>
              <button onClick={() => startMock(m)} disabled={busy || course.questionCount === 0}>Start</button>
              {admin && <>
                <button className="ghost" onClick={() => fillMock(m)} disabled={busy} title="AI drafts the missing questions for you to approve"><Icon name="sparkles" size={15} /> Fill gaps</button>
                <button className="icon-btn danger" aria-label={`Delete ${m.title}`} onClick={() => removeMock(m)}><Icon name="trash" size={15} /></button>
              </>}
            </div>
          ))}
          {admin && (
            <form className="form" onSubmit={createMock} style={{ marginTop: '1rem' }}>
              <label className="wide">New mock test name<input required value={mock.title} onChange={(e) => setMock({ ...mock, title: e.target.value })} placeholder="Advanced Sales Certification mock" /></label>
              <label>Questions<input type="number" min="1" max="200" value={mock.questionCount} onChange={(e) => setMock({ ...mock, questionCount: Number(e.target.value) })} /></label>
              <label>Minutes<input type="number" min="5" max="240" value={mock.minutes} onChange={(e) => setMock({ ...mock, minutes: Number(e.target.value) })} /></label>
              <label>Easy %<input type="number" min="0" max="100" value={mock.easyPct} onChange={(e) => setMock({ ...mock, easyPct: Number(e.target.value) })} /></label>
              <label>Medium %<input type="number" min="0" max="100" value={mock.mediumPct} onChange={(e) => setMock({ ...mock, mediumPct: Number(e.target.value) })} /></label>
              <label>Hard %<input type="number" min="0" max="100" value={mock.hardPct} onChange={(e) => setMock({ ...mock, hardPct: Number(e.target.value) })} /></label>
              <label className="inline"><input type="checkbox" checked={mock.negativeMarking} onChange={(e) => setMock({ ...mock, negativeMarking: e.target.checked })} /> Negative marking</label>
              <button disabled={busy}><Icon name="plus" size={16} /> Create mock test</button>
            </form>
          )}
        </section>
      )}

      {learns && <Flashcards courseId={course.id} admin={admin} />}
      {learns && lessons.length > 0 && <VoiceAssessment courseId={course.id} />}

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
        {groups.map((g, gi) => (
          <div key={gi}>
            {g.key && <h3 className="module-title"><Icon name="layers" size={16} /> {g.key}</h3>}
            <ol className="lessons">
              {g.items.map((l) => (
                <li key={l.id} className={`card lesson ${l.completed ? 'done' : ''}`}>
                  <div className="lesson-top">
                    <span className="lesson-no">{l.completed ? <Icon name="check" size={16} /> : l.order}</span>
                    <button className="link grow" onClick={() => setOpen(open === l.id ? null : l.id)} aria-expanded={open === l.id}>{l.title}{l.videoUrl && <Icon name="video" size={14} />}</button>
                    {course.enrolled && !l.completed && <button className="ghost" onClick={() => complete(l.id)} disabled={busy}>Mark complete</button>}
                  </div>
                  {open === l.id && (l.content
                    ? <>{l.videoUrl && <Video url={l.videoUrl} />}<div className="lesson-body">{l.content}</div></>
                    : <p className="empty">Enrol to read this lesson.</p>)}
                </li>
              ))}
            </ol>
          </div>
        ))}
      </section>

      {admin && (
        <>
          <section className="card upload-card" style={{ marginTop: '2rem' }}>
            <span className="eyebrow"><Icon name="upload" size={14} /> Content engine</span>
            <h2>Bring in your content</h2>
            <p className="empty">The AI turns it into short lessons with objectives, key concepts, definitions, FAQs and practice scenarios. Nothing is shown to learners until you publish.
              Supported: PDF (including scanned), Word, PowerPoint, Excel, text, audio (mp3, wav, m4a), video (mp4, mov, webm), images (png, jpg), SCORM or HTML packages (.zip), and web pages.
              Documents can be up to 15 MB; audio, video and images up to 14 MB.</p>
            <input ref={fileRef} type="file" accept=".pdf,.docx,.pptx,.xlsx,.txt,.md,.csv,.zip,.mp3,.wav,.m4a,.aac,.ogg,.flac,.mp4,.mov,.webm,.png,.jpg,.jpeg,.webp" onChange={onFile} hidden />
            <div className="row" style={{ justifyContent: 'flex-start', marginBottom: '.8rem' }}>
              <button onClick={() => fileRef.current?.click()} disabled={busy}><Icon name="upload" size={16} /> {busy ? 'Reading and structuring…' : 'Choose a file'}</button>
            </div>
            <form className="row" onSubmit={fromUrl} style={{ justifyContent: 'flex-start', marginBottom: 0 }}>
              <input style={{ flex: 1, minWidth: 240 }} type="url" required value={url} onChange={(e) => setUrl(e.target.value)} placeholder="Or paste a web page address" aria-label="Web page address" />
              <button className="ghost" disabled={busy || !url}>Import page</button>
            </form>
            {docs.length > 0 && (
              <ul className="plain" style={{ marginTop: '1rem' }}>
                {docs.map((d) => <li key={d.id}><Icon name="file" size={14} /> {d.filename} <small>{d.kind.toLowerCase()}, {d.lessonsCreated} lessons</small></li>)}
              </ul>
            )}
          </section>

          <section style={{ marginTop: '2rem' }}>
            <h2>Add a lesson by hand</h2>
            <form className="card form" onSubmit={addLesson}>
              <label>Title<input required value={lesson.title} onChange={(e) => setLesson({ ...lesson, title: e.target.value })} /></label>
              <label>Module (optional)<input value={lesson.moduleTitle} maxLength={200} onChange={(e) => setLesson({ ...lesson, moduleTitle: e.target.value })} placeholder="Module 1: Basics" /></label>
              <label className="wide">Video link (optional)<input type="url" value={lesson.videoUrl} onChange={(e) => setLesson({ ...lesson, videoUrl: e.target.value })} placeholder="A YouTube, Vimeo or .mp4 link" /></label>
              <label className="wide">Content<textarea required rows="8" value={lesson.content} onChange={(e) => setLesson({ ...lesson, content: e.target.value })} placeholder="Paste the approved training material for this lesson" /></label>
              <button disabled={busy}><Icon name="plus" size={16} /> Add lesson</button>
            </form>
          </section>

          {settings && (
            <section style={{ marginTop: '2rem' }}>
              <h2>Certification settings</h2>
              <form className="card form" onSubmit={saveSettings}>
                <label className="wide">Certificate title (optional)<input value={settings.certificateTitle} maxLength={200} onChange={(e) => setSettings({ ...settings, certificateTitle: e.target.value })} placeholder="Certified Pharmaceutical Sales Professional" /></label>
                <label>Pass mark (%)<input type="number" min="40" max="100" value={settings.passMark} onChange={(e) => setSettings({ ...settings, passMark: e.target.value })} /></label>
                <label>Final exam questions<input type="number" min="1" max="200" value={settings.examCount} onChange={(e) => setSettings({ ...settings, examCount: e.target.value })} /></label>
                <label>Final exam minutes<input type="number" min="5" max="240" value={settings.examMinutes} onChange={(e) => setSettings({ ...settings, examMinutes: e.target.value })} /></label>
                <label className="inline"><input type="checkbox" checked={settings.requiresSimulation} onChange={(e) => setSettings({ ...settings, requiresSimulation: e.target.checked })} /> Require a finished simulation before the final exam</label>
                <button disabled={busy}>Save settings</button>
              </form>
            </section>
          )}
        </>
      )}
    </>
  );
}
