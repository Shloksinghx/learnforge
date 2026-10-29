import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import Icon from '../Icons.jsx';

const API = import.meta.env.VITE_API_URL || '';

export default function Verify() {
  const { code } = useParams();
  const [cert, setCert] = useState(null);
  const [err, setErr] = useState('');
  useEffect(() => { api(`/api/public/certificates/${code}`).then(setCert).catch(() => setErr('No certificate found with this ID.')); }, [code]);
  const fmt = (d) => new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' });

  return (
    <main className="page narrow">
      <p><Link to="/">&larr; LearnForge</Link></p>
      <span className="eyebrow"><Icon name="shield" size={14} /> Certificate verification</span>
      {err && <p className="error" style={{ marginTop: '1rem' }}>{err}</p>}
      {cert && (
        <div className="card certificate" style={{ marginTop: '1rem' }}>
          <span className={`status-pill ${cert.status.toLowerCase()}`}><Icon name="check" size={16} /> {cert.status}</span>
          <h2>{cert.title.replace('Certificate of Completion: ', '')}</h2>
          <p className="empty">awarded to</p>
          <h1 className="grad-text" style={{ marginBottom: 0 }}>{cert.learnerName}</h1>
          <dl>
            <dt>Certificate ID</dt><dd>{cert.code}</dd>
            <dt>Score</dt><dd>{cert.score}%</dd>
            <dt>Issued by</dt><dd>{cert.issuer}</dd>
            <dt>Issued</dt><dd>{fmt(cert.issuedAt)}</dd>
            <dt>Valid until</dt><dd>{fmt(cert.validUntil)}</dd>
          </dl>
          <img src={`${API}/api/public/certificates/${cert.code}/qr.png`} alt="QR code for this certificate" width="140" height="140" />
          <p style={{ marginTop: '1.2rem' }}><a className="btn" href={`${API}/api/public/certificates/${cert.code}/pdf`} target="_blank" rel="noreferrer"><Icon name="download" size={16} /> Download PDF</a></p>
        </div>
      )}
    </main>
  );
}
