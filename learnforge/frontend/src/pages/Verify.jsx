import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api.js';

export default function Verify() {
  const { code } = useParams();
  const [cert, setCert] = useState(null);
  const [err, setErr] = useState('');
  useEffect(() => { api(`/api/public/certificates/${code}`).then(setCert).catch(() => setErr('No certificate found with this ID.')); }, [code]);
  const fmt = (d) => new Date(d).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' });

  return (
    <main className="page narrow">
      <h1>Certificate verification</h1>
      {err && <p className="error">{err}</p>}
      {cert && (
        <div className="card cert">
          <p className={`status ${cert.status.toLowerCase()}`}>{cert.status}</p>
          <dl>
            <dt>Certificate ID</dt><dd>{cert.code}</dd>
            <dt>Candidate</dt><dd>{cert.learnerName}</dd>
            <dt>Certification</dt><dd>{cert.title}</dd>
            <dt>Score</dt><dd>{cert.score}%</dd>
            <dt>Issued by</dt><dd>{cert.issuer}</dd>
            <dt>Issued</dt><dd>{fmt(cert.issuedAt)}</dd>
            <dt>Valid until</dt><dd>{fmt(cert.validUntil)}</dd>
          </dl>
             <img src={`${import.meta.env.VITE_API_URL || ''}/api/public/certificates/${cert.code}/qr.png`} alt="QR code for this certificate" width="140" height="140" />
        </div>
      )}
    </main>
  );
}
