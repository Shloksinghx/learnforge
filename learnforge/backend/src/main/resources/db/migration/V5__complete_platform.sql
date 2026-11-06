-- Course certification settings, marketplace sharing
ALTER TABLE course
  ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN certificate_title VARCHAR(200),
  ADD COLUMN pass_mark INT NOT NULL DEFAULT 60,
  ADD COLUMN exam_count INT NOT NULL DEFAULT 20,
  ADD COLUMN exam_minutes INT NOT NULL DEFAULT 30,
  ADD COLUMN requires_simulation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE persona ADD COLUMN shared BOOLEAN NOT NULL DEFAULT FALSE;

-- Modules and videos on lessons
ALTER TABLE lesson ADD COLUMN module_title VARCHAR(200), ADD COLUMN video_url VARCHAR(500);

-- Multiple-response questions and answers
ALTER TABLE question ADD COLUMN correct_json TEXT;
ALTER TABLE attempt_answer ADD COLUMN selected_json TEXT;

-- Attempts: pass mark per attempt, named mock tests
ALTER TABLE attempt ADD COLUMN pass_mark INT NOT NULL DEFAULT 60, ADD COLUMN mock_test_id UUID;

-- Teams and plans
ALTER TABLE app_user ADD COLUMN team VARCHAR(100);
ALTER TABLE organisation ADD COLUMN plan VARCHAR(12) NOT NULL DEFAULT 'PRO';

-- Simulations can run from a scenario
ALTER TABLE simulation ADD COLUMN scenario_id UUID;

CREATE TABLE flashcard (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  front TEXT NOT NULL,
  back TEXT NOT NULL,
  lesson_title VARCHAR(255),
  sort_order INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_flashcard_course ON flashcard (course_id, sort_order);

CREATE TABLE course_document (
  id UUID PRIMARY KEY,
  course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  filename VARCHAR(300) NOT NULL,
  kind VARCHAR(20) NOT NULL,
  size_bytes BIGINT NOT NULL,
  lessons_created INT NOT NULL,
  uploaded_by UUID REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE scenario (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  persona_id UUID NOT NULL REFERENCES persona(id) ON DELETE CASCADE,
  course_id UUID REFERENCES course(id) ON DELETE SET NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  objective TEXT,
  expected_behaviour TEXT,
  mandatory_info TEXT,
  prohibited_statements TEXT,
  evaluation_criteria TEXT,
  success_criteria TEXT,
  difficulty INT NOT NULL DEFAULT 3,
  time_limit_minutes INT,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE learning_path (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  title VARCHAR(200) NOT NULL,
  description TEXT,
  level VARCHAR(12) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE learning_path_step (
  id UUID PRIMARY KEY,
  path_id UUID NOT NULL REFERENCES learning_path(id) ON DELETE CASCADE,
  sort_order INT NOT NULL,
  type VARCHAR(12) NOT NULL,
  ref_id UUID NOT NULL,
  label VARCHAR(300) NOT NULL
);

CREATE TABLE notification (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  title VARCHAR(200) NOT NULL,
  body TEXT,
  link VARCHAR(300),
  ref VARCHAR(100),
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (user_id, ref)
);
CREATE INDEX ix_notification_user ON notification (user_id, created_at DESC);

CREATE TABLE mock_test (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  title VARCHAR(200) NOT NULL,
  question_count INT NOT NULL,
  minutes INT NOT NULL,
  easy_pct INT NOT NULL,
  medium_pct INT NOT NULL,
  hard_pct INT NOT NULL,
  negative_marking BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE voice_assessment (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  course_id UUID NOT NULL REFERENCES course(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_user(id),
  question TEXT NOT NULL,
  answer TEXT NOT NULL,
  score INT NOT NULL,
  feedback_json TEXT,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE usage_counter (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  month VARCHAR(7) NOT NULL,
  metric VARCHAR(20) NOT NULL,
  used_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (org_id, month, metric)
);
