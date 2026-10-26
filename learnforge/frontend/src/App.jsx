import { Navigate, NavLink, Route, Routes, Link, useNavigate } from 'react-router-dom';
import { session } from './api.js';
import { isAdmin } from './roles.js';
import { initials, prettyRole } from './ui.js';
import Icon from './Icons.jsx';
import Login from './pages/Login.jsx';
import Home from './pages/Home.jsx';
import Courses from './pages/Courses.jsx';
import CourseDetail from './pages/CourseDetail.jsx';
import Studio from './pages/Studio.jsx';
import Test from './pages/Test.jsx';
import Simulations from './pages/Simulations.jsx';
import Simulation from './pages/Simulation.jsx';
import Users from './pages/Users.jsx';
import Verify from './pages/Verify.jsx';

function Protected({ children }) {
  return session.token ? children : <Navigate to="/login" replace />;
}

function AdminOnly({ children }) {
  return isAdmin(session.user) ? children : <Navigate to="/" replace />;
}

function Shell({ children }) {
  const nav = useNavigate();
  const user = session.user;
  const admin = isAdmin(user);
  return (
    <>
      <header className="bar">
        <Link to="/" className="brand"><span className="brand-mark"><Icon name="sparkles" size={19} /></span>LearnForge</Link>
        <nav className="tabs" aria-label="Main">
          <NavLink to="/" end><Icon name="home" size={16} /> Home</NavLink>
          <NavLink to="/my-courses"><Icon name="book" size={16} /> My courses</NavLink>
          <NavLink to="/courses"><Icon name="grid" size={16} /> Courses</NavLink>
          <NavLink to="/simulations"><Icon name="chat" size={16} /> Simulations</NavLink>
          {admin && <NavLink to="/studio"><Icon name="sparkles" size={16} /> AI Question Studio</NavLink>}
          {admin && <NavLink to="/people"><Icon name="users" size={16} /> People</NavLink>}
        </nav>
        <div className="user-chip">
          <span className="avatar">{initials(user?.fullName)}</span>
          <div><b>{user?.fullName}</b><small>{prettyRole(user?.role)}</small></div>
        </div>
        <button className="ghost icon-btn" aria-label="Sign out" title="Sign out" onClick={() => { session.clear(); nav('/login'); }}>
          <Icon name="logout" size={18} />
        </button>
      </header>
      <main className="page">{children}</main>
    </>
  );
}

const page = (el) => <Protected><Shell>{el}</Shell></Protected>;
const adminPage = (el) => <Protected><AdminOnly><Shell>{el}</Shell></AdminOnly></Protected>;

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/verify/:code" element={<Verify />} />
      <Route path="/" element={page(<Home />)} />
      <Route path="/my-courses" element={page(<Courses mine />)} />
      <Route path="/courses" element={page(<Courses />)} />
      <Route path="/courses/:id" element={page(<CourseDetail />)} />
      <Route path="/studio" element={adminPage(<Studio />)} />
      <Route path="/studio/:courseId" element={adminPage(<Studio />)} />
      <Route path="/people" element={adminPage(<Users />)} />
      <Route path="/test/:attemptId" element={page(<Test />)} />
      <Route path="/simulations" element={page(<Simulations />)} />
      <Route path="/simulate/:id" element={page(<Simulation />)} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
