/** Stable gradient per course title, so each course gets its own look. */
export function coverStyle(title = '') {
  let h = 0;
  for (const ch of title) h = (h * 31 + ch.charCodeAt(0)) % 360;
  return { background: `linear-gradient(135deg, hsl(${h} 78% 52%), hsl(${(h + 60) % 360} 85% 46%))` };
}

export const initials = (name = '') =>
  name.split(' ').filter(Boolean).slice(0, 2).map((s) => s[0].toUpperCase()).join('') || '?';

export const prettyRole = (r = '') => r.replaceAll('_', ' ').toLowerCase();
