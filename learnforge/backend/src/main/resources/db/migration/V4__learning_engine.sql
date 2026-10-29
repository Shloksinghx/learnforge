-- Scenario details and timer for personas
ALTER TABLE persona
  ADD COLUMN objective TEXT,
  ADD COLUMN success_criteria TEXT,
  ADD COLUMN expected_behaviour TEXT,
  ADD COLUMN time_limit_minutes INT;

-- Question kinds: MCQ, TRUE_FALSE, CASE_BASED
ALTER TABLE question ADD COLUMN kind VARCHAR(12) NOT NULL DEFAULT 'MCQ';

-- Mock test options and adaptive mode
ALTER TABLE attempt
  ADD COLUMN mode VARCHAR(10) NOT NULL DEFAULT 'STANDARD',
  ADD COLUMN time_limit_minutes INT,
  ADD COLUMN negative_marking BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN wrong_count INT,
  ADD COLUMN section VARCHAR(10);

-- Gamification
CREATE TABLE xp_event (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  user_id UUID NOT NULL REFERENCES app_user(id),
  type VARCHAR(30) NOT NULL,
  ref_id VARCHAR(64) NOT NULL,
  points INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (user_id, type, ref_id)
);
CREATE INDEX ix_xp_org ON xp_event (org_id, created_at);

CREATE TABLE user_badge (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  user_id UUID NOT NULL REFERENCES app_user(id),
  code VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE (user_id, code)
);
