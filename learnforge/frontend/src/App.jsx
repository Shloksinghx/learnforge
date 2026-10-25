import { Navigate, NavLink, Route, Routes, Link, useNavigate } from 'react-router-dom';
import { session } from './api.js';
import Login from './pages/Login.jsx';
import Home from './pages/Home.jsx';
import Courses from './pages/Courses.jsx';
import CourseDetail from './pages/CourseDetail.jsx';
import Studio from './pages/Studio.jsx';
import Test from './pages/Test.jsx';
import Simulations from './pages/Simulations.jsx';
import Simulation from './pages/Simulation.jsx';
import Verify from './pages/Verify.jsx';

import { AUTHOR_ROLES } from './roles.js';

function Protected({ children }) {
  return session.token ? children : <Navigate to="/login" replace />;
}

function Shell({ children }) {
  const nav = useNavigate();
  const user = session.user;
  const isAuthor = AUTHOR_ROLES.includes(user?.role);
  return (
    <>
      <header className="bar">
        <Link to="/" className="brand">LearnForge</Link>
        <nav className="tabs" aria-label="Main">
          <NavLink to="/" end>Home</NavLink>
          <NavLink to="/my-courses">My courses</NavLink>
          <NavLink to="/courses">Courses</NavLink>
          <NavLink to="/simulations">Simulations</NavLink>
          {isAuthor && <NavLink to="/studio">AI Question Studio</NavLink>}
        </nav>
        <span className="who">{user?.fullName} <small>{user?.role?.replaceAll('_', ' ').toLowerCase()}</small></span>
        <button className="ghost" onClick={() => { session.clear(); nav('/login'); }}>Sign out</button>
      </header>
      <main className="page">{children}</main>
    </>
  );
}

const page = (el) => <Protected><Shell>{el}</Shell></Protected>;

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/verify/:code" element={<Verify />} />
      <Route path="/" element={page(<Home />)} />
      <Route path="/my-courses" element={page(<Courses mine />)} />
      <Route path="/courses" element={page(<Courses />)} />
      <Route path="/courses/:id" element={page(<CourseDetail />)} />
      <Route path="/studio" element={page(<Studio />)} />
      <Route path="/studio/:courseId" element={page(<Studio />)} />
      <Route path="/test/:attemptId" element={page(<Test />)} />
      <Route path="/simulations" element={page(<Simulations />)} />
      <Route path="/simulate/:id" element={page(<Simulation />)} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
