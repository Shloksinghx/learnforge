CREATE TABLE organisation (
  id UUID PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(200) NOT NULL,
  role VARCHAR(40) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (LOWER(email));

CREATE TABLE course (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_course_org ON course (org_id);

CREATE TABLE persona (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  name VARCHAR(150) NOT NULL,
  profession VARCHAR(150) NOT NULL,
  age INT,
  personality VARCHAR(255),
  communication VARCHAR(255),
  knowledge_level VARCHAR(100),
  behaviour TEXT,
  objections TEXT,
  scenario TEXT NOT NULL,
  mandatory_info TEXT,
  prohibited_statements TEXT,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_persona_org ON persona (org_id);

CREATE TABLE simulation (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  user_id UUID NOT NULL REFERENCES app_user(id),
  persona_id UUID NOT NULL REFERENCES persona(id),
  difficulty INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  evaluation_json TEXT,
  overall_score INT,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ
);
CREATE INDEX ix_simulation_user ON simulation (user_id);

CREATE TABLE simulation_turn (
  id UUID PRIMARY KEY,
  simulation_id UUID NOT NULL REFERENCES simulation(id) ON DELETE CASCADE,
  speaker VARCHAR(10) NOT NULL,
  text TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_turn_sim ON simulation_turn (simulation_id, created_at);

CREATE TABLE certificate (
  id UUID PRIMARY KEY,
  org_id UUID NOT NULL REFERENCES organisation(id),
  code VARCHAR(30) NOT NULL UNIQUE,
  learner_id UUID NOT NULL REFERENCES app_user(id),
  learner_name VARCHAR(200) NOT NULL,
  title VARCHAR(255) NOT NULL,
  score INT NOT NULL,
  issuer VARCHAR(200) NOT NULL,
  issued_at TIMESTAMPTZ NOT NULL,
  valid_until TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE
);
