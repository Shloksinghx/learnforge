-- Allow deleting a course (and its questions) without orphaned test answers.
ALTER TABLE attempt_answer DROP CONSTRAINT IF EXISTS attempt_answer_question_id_fkey;
ALTER TABLE attempt_answer
  ADD CONSTRAINT attempt_answer_question_id_cascade
  FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE;
