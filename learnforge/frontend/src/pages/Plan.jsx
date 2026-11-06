import { useEffect, useState } from 'react';
import { api } from '../api.js';
import Icon from '../Icons.jsx';

const Meter = ({ label, used, limit }) => {
  const pct = limit < 0 ? 0 : Math.min(100, Math.round(100 * used / Math.max(1, limit)));
  return (
    <div className="card">
      <span className="eyebrow">{label}</span>
      <h2 style={{ margin: '.3rem 0' }}>{used}{limit < 0 ? '' : ` of ${limit}`}{limit < 0 && ' (unlimited)'}</h2>
      {limit >= 0 && <div className="progress"><i style={{ width: `${pct}%`, background: pct > 85 ? 'var(--bad)' : undefined }} /></div>}
    </div>
  );
};

export default function Plan() {
  const [p, setP] = useState(null);
  const [err, setErr] = useState('');
  useEffect(() => { api('/api/admin/plan').then(setP).catch((e) => setErr(e.message)); }, []);
  if (!p) return <p>{err || 'Loading plan…'}</p>;
  return (
    <>
      <div className="page-head"><div><span className="eyebrow"><Icon name="card" size={14} /> Subscription</span><h1>Plan and usage</h1></div><span className="chip chip-glow">{p.plan}</span></div>
      <p className="empty">Usage this month. AI calls cover tutor answers, question and lesson generation, simulations, voice and coaching.</p>
      <div className="stats">
        <Meter label="AI calls this month" used={p.aiCallsUsed} limit={p.aiCallsLimit} />
        <Meter label="Learners" used={p.learners} limit={p.learnersLimit} />
        <div className="card"><span className="eyebrow">Certificates issued this month</span><h2 style={{ margin: '.3rem 0' }}>{p.certificatesThisMonth}</h2></div>
      </div>
      <div className="card">
        <h3>Billing</h3>
        <p className="empty">Online payment is not connected yet. To change your plan, contact the platform owner. Plans: Free (300 AI calls, 30 learners), Pro (5,000 AI calls, 500 learners) and Enterprise (unlimited).</p>
      </div>
    </>
  );
}
