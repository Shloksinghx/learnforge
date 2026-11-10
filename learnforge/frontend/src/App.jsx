import { useEffect, useRef, useState } from 'react';
import { Navigate, NavLink, Route, Routes, Link, useLocation, useNavigate } from 'react-router-dom';
import { session } from './api.js';
import { isAdmin, isManager, isViewer, isCertifier } from './roles.js';
import { initials, prettyRole } from './ui.js';
import NotificationBell from './NotificationBell.jsx';
import Login from './pages/Login.jsx';
import Home from './pages/Home.jsx';
import Courses from './pages/Courses.jsx';
import CourseDetail from './pages/CourseDetail.jsx';
import Studio from './pages/Studio.jsx';
import Test from './pages/Test.jsx';
import Simulations from './pages/Simulations.jsx';
import Simulation from './pages/Simulation.jsx';
import Users from './pages/Users.jsx';
import Leaderboard from './pages/Leaderboard.jsx';
import Insights from './pages/Insights.jsx';
import Paths from './pages/Paths.jsx';
import Marketplace from './pages/Marketplace.jsx';
import Certificates from './pages/Certificates.jsx';
import Plan from './pages/Plan.jsx';
import Verify from './pages/Verify.jsx';

function Protected({ children }) {
  return session.token ? children : <Navigate to="/login" replace />;
}

function Guard({ allow, children }) {
  return allow(session.user) ? children : <Navigate to="/" replace />;
}

function Manage({ items }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const { pathname } = useLocation();
  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);
  if (items.length === 0) return null;
  const active = items.some((i) => pathname.startsWith(i.to));
  return (
    <div className="menu" ref={ref}>
      <button className={active ? 'on' : ''} aria-expanded={open} aria-haspopup="true" onClick={() => setOpen(!open)}>Manage &#9662;</button>
      {open && <div className="menu-panel">{items.map((i) => <NavLink key={i.to} to={i.to}>{i.label}</NavLink>)}</div>}
    </div>
  );
}

function Shell({ children }) {
  const nav = useNavigate();
  const user = session.user;
  const manage = [
    isAdmin(user) && { to: '/studio', label: 'AI Question Studio' },
    isViewer(user) && { to: '/insights', label: 'Insights' },
    isViewer(user) && { to: '/people', label: 'People' },
    isCertifier(user) && { to: '/certificates', label: 'Certificates' },
    isAdmin(user) && { to: '/marketplace', label: 'Marketplace' },
    isAdmin(user) && { to: '/plan', label: 'Plan and usage' },
  ].filter(Boolean);
  return (
    <>
      <header className="bar">
        <Link to="/" className="brand">LearnForge</Link>
        <nav className="tabs" aria-label="Main">
          <NavLink to="/" end>Home</NavLink>
          <NavLink to="/my-courses">My courses</NavLink>
          <NavLink to="/courses">Courses</NavLink>
          <NavLink to="/paths">Paths</NavLink>
          <NavLink to="/simulations">Simulations</NavLink>
          <NavLink to="/leaderboard">Leaderboard</NavLink>
          <Manage items={manage} />
        </nav>
        <NotificationBell />
        <div className="user-chip">
          <span className="avatar">{initials(user?.fullName)}</span>
          <div><b>{user?.fullName}</b><small>{prettyRole(user?.role)}</small></div>
        </div>
        <button className="ghost" onClick={() => { session.clear(); nav('/login'); }}>Sign out</button>
      </header>
      <main className="page">{children}</main>
    </>
  );
}

const page = (el) => <Protected><Shell>{el}</Shell></Protected>;
const guarded = (allow, el) => <Protected><Guard allow={allow}><Shell>{el}</Shell></Guard></Protected>;

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/verify/:code" element={<Verify />} />
      <Route path="/" element={page(<Home />)} />
      <Route path="/my-courses" element={page(<Courses mine />)} />
      <Route path="/courses" element={page(<Courses />)} />
      <Route path="/courses/:id" element={page(<CourseDetail />)} />
      <Route path="/paths" element={page(<Paths />)} />
      <Route path="/studio" element={guarded(isAdmin, <Studio />)} />
      <Route path="/studio/:courseId" element={guarded(isAdmin, <Studio />)} />
      <Route path="/people" element={guarded(isViewer, <Users />)} />
      <Route path="/insights" element={guarded(isViewer, <Insights />)} />
      <Route path="/certificates" element={guarded(isCertifier, <Certificates />)} />
      <Route path="/marketplace" element={guarded(isAdmin, <Marketplace />)} />
      <Route path="/plan" element={guarded(isAdmin, <Plan />)} />
      <Route path="/leaderboard" element={page(<Leaderboard />)} />
      <Route path="/test/:attemptId" element={page(<Test />)} />
      <Route path="/simulations" element={page(<Simulations />)} />
      <Route path="/simulate/:id" element={page(<Simulation />)} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
