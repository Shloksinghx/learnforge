import { API_BASE, session } from './api.js';

/** Records from the microphone and returns a 16 kHz mono WAV blob (the format the AI service reads best). */
export async function recordWav() {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  const rec = new MediaRecorder(stream);
  const chunks = [];
  rec.ondataavailable = (e) => chunks.push(e.data);
  rec.start();
  return {
    stop: () => new Promise((resolve, reject) => {
      rec.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        try { resolve(await toWav(new Blob(chunks))); } catch (e) { reject(e); }
      };
      rec.stop();
    }),
  };
}

async function toWav(blob) {
  const ctx = new (window.AudioContext || window.webkitAudioContext)();
  const decoded = await ctx.decodeAudioData(await blob.arrayBuffer());
  const rate = 16000;
  const off = new OfflineAudioContext(1, Math.ceil(decoded.duration * rate), rate);
  const src = off.createBufferSource();
  src.buffer = decoded;
  src.connect(off.destination);
  src.start();
  const pcm = (await off.startRendering()).getChannelData(0);
  const buf = new ArrayBuffer(44 + pcm.length * 2);
  const v = new DataView(buf);
  const str = (o, s) => [...s].forEach((c, i) => v.setUint8(o + i, c.charCodeAt(0)));
  str(0, 'RIFF'); v.setUint32(4, 36 + pcm.length * 2, true); str(8, 'WAVEfmt ');
  v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  str(36, 'data'); v.setUint32(40, pcm.length * 2, true);
  pcm.forEach((s, i) => v.setInt16(44 + i * 2, Math.max(-1, Math.min(1, s)) * 0x7fff, true));
  await ctx.close();
  return new Blob([buf], { type: 'audio/wav' });
}

export async function transcribe(wavBlob) {
  const form = new FormData();
  form.append('file', wavBlob, 'speech.wav');
  const res = await fetch(`${API_BASE}/api/voice/transcribe`, { method: 'POST', headers: { Authorization: `Bearer ${session.token}` }, body: form });
  if (!res.ok) throw new Error('Could not understand the recording');
  return (await res.json()).text;
}

/** Plays the AI voice. Falls back to the browser voice if the server voice is unavailable. */
export async function speak(text) {
  try {
    const res = await fetch(`${API_BASE}/api/voice/speak`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` }, body: JSON.stringify({ text }),
    });
    if (!res.ok) throw new Error('voice unavailable');
    const audio = new Audio(URL.createObjectURL(await res.blob()));
    await audio.play();
  } catch {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
      const u = new SpeechSynthesisUtterance(text);
      u.lang = 'en-IN';
      window.speechSynthesis.speak(u);
    }
  }
}
