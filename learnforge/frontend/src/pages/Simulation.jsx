import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, session } from '../api.js';
import { initials } from '../ui.js';
import Icon from '../Icons.jsx';
import ScoreRing from '../ScoreRing.jsx';

const LABELS = {
  productKnowledge: 'Product knowledge', communication: 'Communication', questioning: 'Questioning',
  objectionHandling: 'Objection handling', accuracy: 'Accuracy', compliance: 'Compliance', confidence: 'Confidence',
};
const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;

export default function Simulation() {
  const { id } = useParams();
  const [sim, setSim] = useState(null);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [voice, setVoice] = useState(false);
  const [listening, setListening] = useState(false);
  const [left, setLeft] = useState(null);
  const finishing = useRef(false);
  const recRef = useRef(null);
  const endRef = useRef(null);
  const me = initials(session.user?.fullName);

  useEffect(() => { api(`/api/simulations/${id}`).then(setSim).catch((e) => setErr(e.message)); }, [id]);

  // countdown for personas with a time limit; the evaluation runs automatically when time is up
  useEffect(() => {
    if (!sim || sim.status !== 'ACTIVE' || !sim.timeLimitMinutes) return undefined;
    const tick = () => {
      const remaining = Math.round(sim.timeLimitMinutes * 60 - (Date.now() - new Date(sim.startedAt).getTime()) / 1000);
      setLeft(Math.max(0, remaining));
    };
    tick();
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, [sim?.status, sim?.timeLimitMinutes, sim?.startedAt]);

  useEffect(() => {
    if (left === 0 && sim?.status === 'ACTIVE' && !finishing.current && sim.turns.some((t) => t.speaker === 'LEARNER')) {
      finishing.current = true;
      finish();
    }
  }, [left]);
  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [sim?.turns?.length]);

  const speak = useCallback((t) => {
    if (!voice || !('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(t.replace(/^Hint:.*$/gim, ''));
    u.lang = 'en-IN';
    window.speechSynthesis.speak(u);
  }, [voice]);

  async function send(message) {
    const body = (message ?? text).trim();
    if (!body || busy) return;
    setBusy(true); setErr(''); setText('');
    setSim((s) => ({ ...s, turns: [...s.turns, { speaker: 'LEARNER', text: body }] }));
    try {
      const res = await api(`/api/simulations/${id}/messages`, { method: 'POST', body: { text: body } });
      setSim(res);
      speak(res.turns[res.turns.length - 1].text);
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  function listen() {
    if (!SpeechRec) { setErr('Voice input is not supported in this browser. Use Chrome or Edge, or type your reply.'); return; }
    if (listening) { recRef.current?.stop(); return; }
    const rec = new SpeechRec();
    rec.lang = 'en-IN';
    rec.interimResults = false;
    rec.onresult = (e) => send(e.results[0][0].transcript);
    rec.onend = () => setListening(false);
    rec.onerror = () => setListening(false);
    recRef.current = rec;
    setListening(true);
    rec.start();
  }

  async function finish() {
    setBusy(true); setErr('');
    try { setSim(await api(`/api/simulations/${id}/finish`, { method: 'POST' })); }
    catch (e) { setErr(e.message); } finally { setBusy(false); }
  }

  if (!sim) return <p>{err || 'Loading simulation…'}</p>;
  const finished = sim.status === 'FINISHED';
  const ev = sim.evaluation;

  return (
    <>
      <div className="row">
        <Link to="/simulations">&larr; All simulations</Link>
        <label className="inline"><input type="checkbox" checked={voice} onChange={(e) => setVoice(e.target.checked)} /> Speak replies aloud</label>
      </div>
      <div className="card sim-head">
        <span className="avatar big">{initials(sim.personaName || 'AI')}</span>
        <div className="grow"><b>{sim.personaName || 'AI persona'}</b><br /><small>{sim.personaProfession} &middot; difficulty {sim.difficulty}</small></div>
        {left != null && !finished && <span className={`timer ${left < 30 ? 'low' : ''}`}><Icon name="clock" size={16} /> {String(Math.floor(left / 60)).padStart(2, '0')}:{String(left % 60).padStart(2, '0')}</span>}
      </div>

      <div className="card chat" aria-live="polite">
        {sim.turns.map((t, i) => (
          <div key={i} className={`msg ${t.speaker === 'LEARNER' ? 'me' : ''}`}>
            <span className="avatar">{t.speaker === 'LEARNER' ? me : 'AI'}</span>
            <div className="bubble">{t.text}</div>
          </div>
        ))}
        {busy && !finished && <div className="msg"><span className="avatar">AI</span><div className="bubble dim">Thinking…</div></div>}
        <div ref={endRef} />
      </div>
      {err && <p className="error" role="alert">{err}</p>}

      {!finished && (
        <>
          <form className="composer" onSubmit={(e) => { e.preventDefault(); send(); }}>
            <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Type your reply" aria-label="Your reply" />
            <button disabled={busy || !text.trim()}>Send</button>
            <button type="button" className={listening ? 'rec' : 'ghost'} onClick={listen}><Icon name="mic" size={16} /> {listening ? 'Listening… tap to stop' : 'Speak'}</button>
          </form>
          <button className="ghost" onClick={finish} disabled={busy || sim.turns.length < 3}><Icon name="check" size={16} /> End and get my evaluation</button>
        </>
      )}

      {finished && ev && (
        <section className="card pad-lg" style={{ marginTop: '1.5rem' }}>
          <div className="result-hero">
            <ScoreRing value={sim.overallScore ?? 0} size={130} />
            <div><h2>Your evaluation</h2><p className="empty">Scored on what you said in this conversation.</p></div>
          </div>
          <div className="bars">
            {Object.entries(LABELS).map(([k, label]) => (
              <div key={k} className="bar-row">
                <span>{label}</span>
                <div className="track"><i style={{ width: `${ev.scores?.[k] ?? 0}%` }} /></div>
                <b>{ev.scores?.[k] ?? 0}%</b>
              </div>
            ))}
          </div>
          {[['What you did well', ev.didWell], ['What you missed', ev.missed], ['What to improve', ev.improve], ['Recommended learning', ev.recommendedLearning]]
            .map(([title, items]) => items?.length > 0 && (
              <div key={title}><h3>{title}</h3><ul>{items.map((x, i) => <li key={i}>{x}</li>)}</ul></div>
            ))}
          <Link to="/simulations" className="btn">Try another simulation <Icon name="arrow" size={16} /></Link>
        </section>
      )}
    </>
  );
}
