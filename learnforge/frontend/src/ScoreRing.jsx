export default function ScoreRing({ value = 0, size = 120 }) {
  const v = Math.min(100, Math.max(0, value));
  const r = 52;
  const c = 2 * Math.PI * r;
  const id = `ring-${size}-${v}`;
  return (
    <svg width={size} height={size} viewBox="0 0 120 120" role="img" aria-label={`${v} percent`} className="ring">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#7C5CFF" />
          <stop offset="1" stopColor="#22D3EE" />
        </linearGradient>
      </defs>
      <circle cx="60" cy="60" r={r} fill="none" stroke="rgba(255,255,255,.1)" strokeWidth="10" />
      <circle cx="60" cy="60" r={r} fill="none" stroke={`url(#${id})`} strokeWidth="10" strokeLinecap="round"
              strokeDasharray={c} strokeDashoffset={c * (1 - v / 100)} transform="rotate(-90 60 60)"
              style={{ transition: 'stroke-dashoffset .9s ease' }} />
      <text x="60" y="67" textAnchor="middle" fontSize="26" fontWeight="800" fill="currentColor" fontFamily="Sora, sans-serif">{v}%</text>
    </svg>
  );
}
