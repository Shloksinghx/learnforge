import { useState } from 'react';
import { api } from './api.js';
import { recordWav, transcribe } from './voice.js';
import Icon from './Icons.jsx';
import ScoreRing from './ScoreRing.jsx';

const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;

export default function VoiceAssessment({ courseId }) {
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState('');
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(null);
  const [listening, setListening] = useState(false);
  const [err, setErr] = useState('');

  async function ask() {
    setBusy(true); setErr(''); setResult(null); setAnswer('');
    try { setQuestion((await api(`/api/courses/${courseId}/voice/question`, { method: 'POST' })).question); }
    catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  async function serverRecord() {
    if (recording) {
      const r = recording; setRecording(null); setBusy(true);
      try { const t = await transcribe(await r.stop()); setAnswer((a) => `${a} ${t}`.trim()); } catch (e) { setErr(e.message); } finally { setBusy(false); }
      return;
    }
    try { setErr(''); setRecording(await recordWav()); } catch { setErr('Microphone access was blocked.'); }
  }

  function browserListen() {
    if (!SpeechRec) { serverRecord(); return; }
    const rec = new SpeechRec();
    rec.lang = 'en-IN'; rec.interimResults = false;
    rec.onresult = (e) => setAnswer((a) => `${a} ${e.results[0][0].transcript}`.trim());
    rec.onend = () => setListening(false);
    rec.onerror = () => setListening(false);
    setListening(true); rec.start();
  }

  async function evaluate() {
    setBusy(true); setErr('');
    try { setResult(await api(`/api/courses/${courseId}/voice/evaluate`, { method: 'POST', body: { question, answer } })); }
    catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  const fb = result?.feedback;
  return (
    <section className="card" style={{ marginTop: '1.5rem' }}>
      <span className="eyebrow"><Icon name="mic" size={14} /> Voice assessment</span>
      <h2>Answer out loud</h2>
      <p className="empty">The AI asks an open question from the lessons. Speak your answer (or type it) and it is marked against the course material.</p>
      {err && <p className="error" role="alert">{err}</p>}
      {!question && <button onClick={ask} disabled={busy}><Icon name="mic" size={16} /> {busy ? 'Preparing…' : 'Get a question'}</button>}
      {question && (
        <>
          <p className="coach-msg">{question}</p>
          <label>Your answer<textarea rows="4" value={answer} onChange={(e) => setAnswer(e.target.value)} placeholder="Tap Speak and answer, or type here" /></label>
          <div className="row" style={{ justifyContent: 'flex-start', marginTop: '.8rem', marginBottom: 0 }}>
            <button className={listening || recording ? 'rec' : 'ghost'} onClick={SpeechRec ? browserListen : serverRecord} disabled={busy && !recording}>
              <Icon name="mic" size={16} /> {recording ? 'Recording… tap to add' : listening ? 'Listening…' : 'Speak'}
            </button>
            <button onClick={evaluate} disabled={busy || answer.trim().length < 3}>Mark my answer</button>
            <button className="ghost" onClick={ask} disabled={busy}><Icon name="repeat" size={15} /> New question</button>
          </div>
        </>
      )}
      {fb && (
        <div className="result-hero" style={{ marginTop: '1.2rem', marginBottom: 0 }}>
          <ScoreRing value={result.score} size={110} />
          <div>
            <p>{fb.feedback}</p>
            <small>Accuracy {fb.accuracy}%, completeness {fb.completeness}%, clarity {fb.clarity}%</small>
            {fb.strengths?.length > 0 && <p><b className="ok">Strengths:</b> {fb.strengths.join('; ')}</p>}
            {fb.missed?.length > 0 && <p><b className="bad">Missed:</b> {fb.missed.join('; ')}</p>}
          </div>
        </div>
      )}
    </section>
  );
}
