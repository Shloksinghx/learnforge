import { Navigate, Route, Routes, Link, useNavigate } from 'react-router-dom';
import { session } from './api.js';
import Login from './pages/Login.jsx';
import Home from './pages/Home.jsx';
import Simulation from './pages/Simulation.jsx';
import Verify from './pages/Verify.jsx';

function Protected({ children }) {
  return session.token ? children : <Navigate to="/login" replace />;
}

function Shell({ children }) {
  const nav = useNavigate();
  const user = session.user;
  return (
    <>
      <header className="bar">
        <Link to="/" className="brand">LearnForge</Link>
        <span className="who">{user?.fullName} <small>{user?.role?.replaceAll('_', ' ').toLowerCase()}</small></span>
        <button className="ghost" onClick={() => { session.clear(); nav('/login'); }}>Sign out</button>
      </header>
      <main className="page">{children}</main>
    </>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/verify/:code" element={<Verify />} />
      <Route path="/" element={<Protected><Shell><Home /></Shell></Protected>} />
      <Route path="/simulate/:id" element={<Protected><Shell><Simulation /></Shell></Protected>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
