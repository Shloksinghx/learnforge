ALTER TABLE course ADD COLUMN category VARCHAR(100);

CREATE TABLE lesson (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  sort_order INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_lesson_course ON lesson (course_id, sort_order);

CREATE TABLE enrolment (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (course_id, user_id)
);

CREATE TABLE lesson_completion (
  id UUID PRIMARY KEY,
  lesson_id UUID NOT NULL REFERENCES lesson(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (lesson_id, user_id)
);

CREATE TABLE question (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  text TEXT NOT NULL,
  options_json TEXT NOT NULL,
  correct_index INT NOT NULL,
  explanation TEXT,
  difficulty VARCHAR(10) NOT NULL,
  status VARCHAR(10) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_question_course ON question (course_id, status);

CREATE TABLE attempt (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id),
  status VARCHAR(15) NOT NULL,
  score INT,
  correct_count INT,
  total INT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  submitted_at TIMESTAMPTZ
);
CREATE INDEX ix_attempt_user ON attempt (user_id, started_at DESC);

CREATE TABLE attempt_answer (
  id UUID PRIMARY KEY,
  attempt_id UUID NOT NULL REFERENCES attempt(id) ON DELETE CASCADE,
  question_id UUID NOT NULL REFERENCES question(id),
  sort_order INT NOT NULL,
  selected_index INT,
  correct BOOLEAN
);
CREATE INDEX ix_attempt_answer ON attempt_answer (attempt_id, sort_order);
