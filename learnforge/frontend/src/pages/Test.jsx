import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';

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
      window.scrollTo({ top: 0 });
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  if (!attempt) return <p>{err || 'Loading test…'}</p>;
  const done = attempt.status === 'SUBMITTED';
  const answered = Object.keys(picked).length;

  return (
    <>
      <p><Link to={`/courses/${attempt.courseId}`}>Back to course</Link></p>
      <h1>{attempt.courseTitle}: test</h1>
      {err && <p className="error" role="alert">{err}</p>}

      {done && (
        <div className="card result">
          <h2>{attempt.score}%: {attempt.passed ? 'Passed' : 'Not passed yet'}</h2>
          <p>{attempt.correctCount} of {attempt.total} correct. Pass mark is 60%.</p>
        </div>
      )}

      {attempt.questions.map((q, n) => (
        <fieldset key={q.id} className="card q" disabled={done}>
          <legend><b>{n + 1}. {q.text}</b></legend>
          {q.options.map((o, i) => {
            const chosen = done ? q.selectedIndex === i : picked[q.id] === i;
            const cls = done ? (i === q.correctIndex ? 'correct' : chosen ? 'wrong' : '') : '';
            return (
              <label key={i} className={`choice ${cls}`}>
                <input type="radio" name={q.id} checked={chosen} onChange={() => setPicked({ ...picked, [q.id]: i })} />
                {o}
              </label>
            );
          })}
          {done && (
            <p className={q.correct ? 'ok' : 'bad'}>
              {q.correct ? 'Correct.' : q.selectedIndex == null ? 'Not answered.' : 'Incorrect.'} {q.explanation}
            </p>
          )}
        </fieldset>
      ))}

      {!done
        ? <div className="row"><small>{answered} of {attempt.total} answered</small><button onClick={submit} disabled={busy}>Submit test</button></div>
        : <Link className="btn" to={`/courses/${attempt.courseId}`}>Back to course</Link>}
    </>
  );
}
