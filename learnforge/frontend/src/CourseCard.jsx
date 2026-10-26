import { Link } from 'react-router-dom';
import Icon from './Icons.jsx';
import { coverStyle } from './ui.js';

export default function CourseCard({ course: c, admin, onEnrol, onDelete }) {
  return (
    <article className="card course-card">
      <Link to={`/courses/${c.id}`} className="cover" style={coverStyle(c.title)} tabIndex={-1} aria-hidden="true">
        <span className="cover-letter">{c.title[0]}</span>
        {c.status === 'DRAFT' && <span className="chip chip-draft">Draft</span>}
      </Link>
      <div className="course-body">
        <span className="chip">{c.category || 'Course'}</span>
        <h3><Link to={`/courses/${c.id}`}>{c.title}</Link></h3>
        <p className="clamp">{c.description || 'No description yet.'}</p>
        <div className="meta">
          <span><Icon name="book" size={14} /> {c.lessonCount} lessons</span>
          <span><Icon name="target" size={14} /> {c.questionCount} questions</span>
        </div>
        {c.enrolled && (
          <div className="progress-wrap">
            <div className="progress" role="progressbar" aria-valuenow={c.progress} aria-valuemin="0" aria-valuemax="100"><i style={{ width: `${c.progress}%` }} /></div>
            <small>{c.progress}%</small>
          </div>
        )}
        <div className="card-actions">
          {c.enrolled || admin
            ? <Link className="btn" to={`/courses/${c.id}`}>{c.enrolled ? 'Continue' : 'Open'} <Icon name="arrow" size={16} /></Link>
            : <button onClick={() => onEnrol(c.id)}>Enrol now</button>}
          {admin && (
            <button className="icon-btn danger" aria-label={`Delete ${c.title}`} title="Delete course" onClick={() => onDelete(c)}>
              <Icon name="trash" size={17} />
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
