import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import Icon from '../Icons.jsx';
import ScoreRing from '../ScoreRing.jsx';

const L = ['A', 'B', 'C', 'D'];
const mmss = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
const dur = (s) => (s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`);
const KIND = { TRUE_FALSE: 'True or false', CASE_BASED: 'Case study', MCQ: '' };

function Options({ q, chosen, onPick, reveal }) {
  return q.options.map((o, i) => {
    const isChosen = chosen === i;
    const cls = reveal ? (i === q.correctIndex ? 'correct' : isChosen ? 'wrong' : '') : isChosen ? 'selected' : '';
    return (
      <label key={i} className={`option ${cls}`}>
        <input type="radio" name={q.id} checked={isChosen} disabled={reveal} onChange={() => onPick(i)} />
        <span className="letter">{L[i]}</span>{o}
      </label>
    );
  });
}

export default function Test() {
  const { attemptId } = useParams();
  const storeKey = `attempt-${attemptId}`;
  const [attempt, setAttempt] = useState(null);
  const [picked, setPicked] = useState({});
  const [flagged, setFlagged] = useState({});
  const [cur, setCur] = useState(0);
  const [left, setLeft] = useState(null);
  const [feedback, setFeedback] = useState(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const submitting = useRef(false);

  useEffect(() => {
    api(`/api/attempts/${attemptId}`).then((a) => {
      setAttempt(a);
      if (a.status === 'IN_PROGRESS' && a.secondsLeft != null) setLeft(a.secondsLeft);
      try {
        const saved = JSON.parse(sessionStorage.getItem(storeKey) || 'null');
        if (saved && a.status === 'IN_PROGRESS') { setPicked(saved.picked || {}); setFlagged(saved.flagged || {}); }
      } catch { /* ignore */ }
    }).catch((e) => setErr(e.message));
  }, [attemptId]);

  useEffect(() => {
    if (attempt?.status === 'IN_PROGRESS') sessionStorage.setItem(storeKey, JSON.stringify({ picked, flagged }));
  }, [picked, flagged]);

  // countdown, and auto-submit when time runs out
  useEffect(() => {
    if (left == null || attempt?.status !== 'IN_PROGRESS' || attempt.mode !== 'STANDARD') return undefined;
    if (left <= 0) { submit(true); return undefined; }
    const t = setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [left, attempt?.status]);

  async function submit(auto = false) {
    if (submitting.current || !attempt) return;
    const unanswered = attempt.questions.filter((q) => picked[q.id] == null).length;
    if (!auto && unanswered > 0 && !window.confirm(`${unanswered} question(s) are unanswered. Submit anyway?`)) return;
    submitting.current = true; setErr(''); setBusy(true);
    try {
      const answers = attempt.questions.map((q) => ({ questionId: q.id, selectedIndex: picked[q.id] ?? null }));
      setAttempt(await api(`/api/attempts/${attemptId}/submit`, { method: 'POST', body: { answers } }));
      sessionStorage.removeItem(storeKey);
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) { setErr(e.message); submitting.current = false; } finally { setBusy(false); }
  }

  async function checkAdaptive(q) {
    setErr(''); setBusy(true);
    try { setFeedback(await api(`/api/attempts/${attemptId}/answer`, { method: 'POST', body: { questionId: q.id, selectedIndex: picked[q.id] } })); }
    catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  if (!attempt) return <p>{err || 'Loading test…'}</p>;
  const done = attempt.status === 'SUBMITTED';
  const adaptive = attempt.mode === 'ADAPTIVE';

  /* ---------- results ---------- */
  if (done) {
    return (
      <>
        <p><Link to={`/courses/${attempt.courseId}`}>&larr; Back to course</Link></p>
        <h1>{attempt.courseTitle}</h1>
        {err && <p className="error" role="alert">{err}</p>}
        <div className="card result-hero pad-lg">
          <ScoreRing value={attempt.score} size={140} />
          <div>
            <h2>{attempt.passed ? 'You passed!' : 'Not passed yet'}</h2>
            <p className="empty">
              {attempt.correctCount} correct, {attempt.wrongCount} wrong, {attempt.total - attempt.correctCount - attempt.wrongCount} unanswered
              {attempt.timeTakenSeconds != null && `, finished in ${dur(attempt.timeTakenSeconds)}`}.
              {attempt.negativeMarking && ' Negative marking was on.'} {adaptive && 'Adaptive practice.'} The pass mark is 60%.
            </p>
            {attempt.certificateCode && <a className="btn" href={`/verify/${attempt.certificateCode}`}><Icon name="award" size={16} /> View your certificate</a>}
            {attempt.certificateNote && <p className="error">{attempt.certificateNote}</p>}
            {!attempt.passed && <p>Review the explanations below, then try again from the course page.</p>}
          </div>
        </div>

        {attempt.analysis.length > 0 && (
          <section className="card" style={{ marginBottom: '1.5rem' }}>
            <h3>Result analysis by difficulty</h3>
            <div className="bars">
              {attempt.analysis.map((b) => (
                <div key={b.difficulty} className="bar-row">
                  <span style={{ textTransform: 'capitalize' }}>{b.difficulty.toLowerCase()}</span>
                  <div className="track"><i style={{ width: `${Math.round(100 * b.correct / b.total)}%` }} /></div>
                  <b>{b.correct}/{b.total}</b>
                </div>
              ))}
            </div>
          </section>
        )}

        {attempt.questions.map((q, n) => (
          <fieldset key={q.id} className="card qcard" disabled>
            <legend>{n + 1}. {KIND[q.kind] && <span className="chip">{KIND[q.kind]}</span>} {q.text}</legend>
            <Options q={q} chosen={q.selectedIndex} reveal onPick={() => {}} />
            <p className="explain"><b className={q.correct ? 'ok' : 'bad'}>{q.correct ? 'Correct. ' : q.selectedIndex == null ? 'Not answered. ' : 'Incorrect. '}</b>{q.explanation}</p>
          </fieldset>
        ))}
      </>
    );
  }

  /* ---------- adaptive practice ---------- */
  if (adaptive) {
    const answeredCount = attempt.questions.filter((q) => q.correct != null).length;
    const q = attempt.questions.find((x) => x.correct == null);
    return (
      <>
        <p><Link to={`/courses/${attempt.courseId}`}>&larr; Back to course</Link></p>
        <h1>{attempt.courseTitle}</h1>
        <div className="card test-bar">
          <span className="chip chip-glow">Adaptive practice</span>
          <small>Question {Math.min(answeredCount + 1, attempt.total)} of {attempt.total}</small>
          <div className="progress"><i style={{ width: `${Math.round(100 * answeredCount / attempt.total)}%` }} /></div>
        </div>
        {err && <p className="error" role="alert">{err}</p>}

        {feedback ? (
          <div className="card qcard">
            <h2 className={feedback.correct ? 'ok' : 'bad'}>{feedback.correct ? 'Correct!' : 'Not quite'}</h2>
            <p className="explain">{feedback.explanation}</p>
            {feedback.remediation && (
              <div className="remedy"><span className="eyebrow"><Icon name="bulb" size={14} /> Quick review: {feedback.remediation.lessonTitle}</span><p>{feedback.remediation.excerpt}</p></div>
            )}
            <button onClick={() => { setAttempt(feedback.attempt); setFeedback(null); }}>
              {feedback.attempt.status === 'SUBMITTED' ? 'See my results' : 'Next question'} <Icon name="arrow" size={16} />
            </button>
          </div>
        ) : q && (
          <fieldset className="card qcard">
            <legend>{KIND[q.kind] && <span className="chip">{KIND[q.kind]}</span>} {q.text}</legend>
            <Options q={q} chosen={picked[q.id]} onPick={(i) => setPicked({ ...picked, [q.id]: i })} />
            <div className="row" style={{ justifyContent: 'flex-end', marginTop: '.8rem', marginBottom: 0 }}>
              <button onClick={() => checkAdaptive(q)} disabled={busy || picked[q.id] == null}>Check answer</button>
            </div>
          </fieldset>
        )}
      </>
    );
  }

  /* ---------- standard mock test ---------- */
  const q = attempt.questions[cur];
  const answered = attempt.questions.filter((x) => picked[x.id] != null).length;
  return (
    <>
      <p><Link to={`/courses/${attempt.courseId}`}>&larr; Back to course</Link></p>
      <h1>{attempt.courseTitle}</h1>
      {err && <p className="error" role="alert">{err}</p>}
      <div className="card test-bar">
        {left != null && <span className={`timer ${left < 60 ? 'low' : ''}`}><Icon name="clock" size={16} /> {mmss(Math.max(0, left))}</span>}
        <small>{answered} of {attempt.total} answered{attempt.negativeMarking ? ', negative marking on' : ''}</small>
        <div className="progress"><i style={{ width: `${Math.round(100 * answered / attempt.total)}%` }} /></div>
        <button onClick={() => submit(false)} disabled={busy}>Submit test</button>
      </div>

      <div className="test-layout">
        <fieldset className="card qcard">
          <legend>{cur + 1}. {KIND[q.kind] && <span className="chip">{KIND[q.kind]}</span>} {q.text}</legend>
          <Options q={q} chosen={picked[q.id]} onPick={(i) => setPicked({ ...picked, [q.id]: i })} />
          <div className="row" style={{ marginTop: '1rem', marginBottom: 0 }}>
            <button className="ghost" onClick={() => setCur(Math.max(0, cur - 1))} disabled={cur === 0}>Previous</button>
            <button className={`ghost ${flagged[q.id] ? 'flagged' : ''}`} onClick={() => setFlagged({ ...flagged, [q.id]: !flagged[q.id] })}><Icon name="flag" size={16} /> {flagged[q.id] ? 'Flagged' : 'Flag for review'}</button>
            {picked[q.id] != null && <button className="ghost" onClick={() => { const p = { ...picked }; delete p[q.id]; setPicked(p); }}>Clear</button>}
            <button onClick={() => setCur(Math.min(attempt.total - 1, cur + 1))} disabled={cur >= attempt.total - 1}>Next</button>
          </div>
        </fieldset>

        <aside className="card palette" aria-label="Question navigator">
          <h3>Questions</h3>
          <div className="palette-grid">
            {attempt.questions.map((x, i) => (
              <button key={x.id} className={`pal ${i === cur ? 'current' : ''} ${picked[x.id] != null ? 'answered' : ''} ${flagged[x.id] ? 'flag' : ''}`}
                onClick={() => setCur(i)} aria-label={`Question ${i + 1}${picked[x.id] != null ? ', answered' : ''}${flagged[x.id] ? ', flagged' : ''}`}>{i + 1}</button>
            ))}
          </div>
          <small>Filled = answered, amber = flagged</small>
        </aside>
      </div>
    </>
  );
}
