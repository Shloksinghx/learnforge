const BASE = import.meta.env.VITE_API_URL || '';

export const session = {
  get token() { return sessionStorage.getItem('token'); },
  get user() { const u = sessionStorage.getItem('user'); return u ? JSON.parse(u) : null; },
  set({ token, user }) { sessionStorage.setItem('token', token); sessionStorage.setItem('user', JSON.stringify(user)); },
  clear() { sessionStorage.clear(); },
};

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (session.token) headers.Authorization = `Bearer ${session.token}`;
  const res = await fetch(`${BASE}${path}`, { method, headers, body: body ? JSON.stringify(body) : undefined });
  if (res.status === 401 && session.token) { session.clear(); window.location.assign('/login'); }
  if (!res.ok) {
    let msg = res.statusText;
    try { const j = await res.json(); msg = j.error || j.message || msg; } catch { /* no body */ }
    throw new Error(msg || `Request failed (${res.status})`);
  }
  return res.status === 204 ? null : res.json();
}
