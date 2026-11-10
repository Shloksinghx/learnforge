export default function ScoreRing({ value = 0, size = 120 }) {
  const v = Math.min(100, Math.max(0, value));
  const r = 52;
  const c = 2 * Math.PI * r;
  return (
    <svg width={size} height={size} viewBox="0 0 120 120" role="img" aria-label={`${v} percent`} className="ring" style={{ color: 'var(--accent)' }}>
      <circle cx="60" cy="60" r={r} fill="none" stroke="var(--line)" strokeWidth="6" />
      <circle cx="60" cy="60" r={r} fill="none" stroke="currentColor" strokeWidth="6" strokeLinecap="round"
              strokeDasharray={c} strokeDashoffset={c * (1 - v / 100)} transform="rotate(-90 60 60)"
              style={{ transition: 'stroke-dashoffset .8s ease' }} />
      <text x="60" y="68" textAnchor="middle" fontSize="30" fill="var(--text)" fontFamily="Instrument Serif, Georgia, serif">{v}%</text>
    </svg>
  );
}
