/** A stable, soft tint per course title. The colours are computed in CSS from --h, so light and dark mode both work. */
export function coverStyle(title = '') {
  let h = 0;
  for (const ch of title) h = (h * 31 + ch.charCodeAt(0)) % 360;
  return { '--h': h };
}

export const initials = (name = '') =>
  name.split(' ').filter(Boolean).slice(0, 2).map((s) => s[0].toUpperCase()).join('') || '?';

export const prettyRole = (r = '') => r.replaceAll('_', ' ').toLowerCase();
