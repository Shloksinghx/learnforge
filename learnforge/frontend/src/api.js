const BASE = import.meta.env.VITE_API_URL || '';

export const session = {
  get token() { return sessionStorage.getItem('token'); },
  get user() { const u = sessionStorage.getItem('user'); return u ? JSON.parse(u) : null; },
  set({ token, user }) { sessionStorage.setItem('token', token); sessionStorage.setItem('user', JSON.stringify(user)); },
  clear() { sessionStorage.clear(); },
};

function authHeaders(json = true) {
  const h = {};
  if (json) h['Content-Type'] = 'application/json';
  if (session.token) h.Authorization = `Bearer ${session.token}`;
  return h;
}

async function fail(res) {
  if (res.status === 401 && session.token) { session.clear(); window.location.assign('/login'); }
  let msg = res.statusText;
  try { const j = await res.json(); msg = j.error || j.message || msg; } catch { /* no body */ }
  throw new Error(msg || `Request failed (${res.status})`);
}

export async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch(`${BASE}${path}`, { method, headers: authHeaders(), body: body ? JSON.stringify(body) : undefined });
  if (!res.ok) await fail(res);
  return res.status === 204 ? null : res.json();
}

/** Upload a file as multipart form data. */
export async function upload(path, file) {
  const form = new FormData();
  form.append('file', file);
  const res = await fetch(`${BASE}${path}`, { method: 'POST', headers: authHeaders(false), body: form });
  if (!res.ok) await fail(res);
  return res.json();
}

/** Download a protected file (for example a CSV report). */
export async function download(path, filename) {
  const res = await fetch(`${BASE}${path}`, { headers: authHeaders(false) });
  if (!res.ok) await fail(res);
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  URL.revokeObjectURL(url);
}

export const API_BASE = BASE;
