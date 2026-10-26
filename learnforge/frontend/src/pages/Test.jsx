import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import Icon from '../Icons.jsx';
import ScoreRing from '../ScoreRing.jsx';

const L = ['A', 'B', 'C', 'D'];

export default function Test() {
  const { attemptId } = useParams();
  const [attempt, setAttempt] = useState(null);
  const [picked, setPicked] = useState({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  useEffect(() => { api(`/api/attempts/${attemptId}`).then(setAttempt).catch((e) => setErr(e.message)); }, [attemptId]);

  async function submit() {
    setErr(''); setBusy(true);
    try {
      const answers = attempt.questions.map((q) => ({ questionId: q.id, selectedIndex: picked[q.id] ?? null }));
      setAttempt(await api(`/api/attempts/${attemptId}/submit`, { method: 'POST', body: { answers } }));
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  if (!attempt) return <p>{err || 'Loading test…'}</p>;
  const done = attempt.status === 'SUBMITTED';
  const answered = Object.keys(picked).length;
  const pct = Math.round((100 * answered) / Math.max(1, attempt.total));

  return (
    <>
      <p><Link to={`/courses/${attempt.courseId}`}>&larr; Back to course</Link></p>
      <h1>{attempt.courseTitle}</h1>
      {err && <p className="error" role="alert">{err}</p>}

      {done ? (
        <div className="card result-hero pad-lg">
          <ScoreRing value={attempt.score} size={140} />
          <div>
            <h2>{attempt.passed ? 'You passed!' : 'Not passed yet'}</h2>
            <p className="empty">{attempt.correctCount} of {attempt.total} correct. The pass mark is 60%.</p>
            {attempt.certificateCode && (
              <a className="btn" href={`/verify/${attempt.certificateCode}`}><Icon name="award" size={16} /> View your certificate</a>
            )}
            {!attempt.passed && <p>Review the explanations below, then try again from the course page.</p>}
          </div>
        </div>
      ) : (
        <div className="card test-bar">
          <small>{answered} of {attempt.total} answered</small>
          <div className="progress"><i style={{ width: `${pct}%` }} /></div>
          <button onClick={submit} disabled={busy}>Submit test</button>
        </div>
      )}

      {attempt.questions.map((q, n) => (
        <fieldset key={q.id} className="card qcard" disabled={done}>
          <legend>{n + 1}. {q.text}</legend>
          {q.options.map((o, i) => {
            const chosen = done ? q.selectedIndex === i : picked[q.id] === i;
            const cls = done ? (i === q.correctIndex ? 'correct' : chosen ? 'wrong' : '') : chosen ? 'selected' : '';
            return (
              <label key={i} className={`option ${cls}`}>
                <input type="radio" name={q.id} checked={chosen} onChange={() => setPicked({ ...picked, [q.id]: i })} />
                <span className="letter">{L[i]}</span>{o}
              </label>
            );
          })}
          {done && (
            <p className="explain">
              <b className={q.correct ? 'ok' : 'bad'}>{q.correct ? 'Correct. ' : q.selectedIndex == null ? 'Not answered. ' : 'Incorrect. '}</b>{q.explanation}
            </p>
          )}
        </fieldset>
      ))}

      {!done && <div className="row" style={{ justifyContent: 'flex-end' }}><button onClick={submit} disabled={busy}>Submit test</button></div>}
    </>
  );
}
