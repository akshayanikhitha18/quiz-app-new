-- =============================================================================
-- QuizSphere - database schema
-- Requires MySQL 8.0.16 or newer (CHECK constraints are enforced from 8.0.16).
--
-- Run it with:  mysql -u root -p < database/schema.sql
-- All timestamps are stored in UTC (the Java layer sets time_zone = '+00:00').
--
-- NORMALIZATION NOTES (useful for viva)
--   1NF : every column holds one atomic value (no comma-separated lists; each
--         answer option is its own row in question_options).
--   2NF : tables with composite keys (quiz_questions, attempt_answers) have no
--         column that depends on only part of the key.
--   3NF : no transitive dependencies. Example: a question stores only topic_id;
--         the subject is found through topics.subject_id, so subject is NOT
--         duplicated inside questions. Likewise, the percentage of an attempt
--         is not stored: it is derived in the view v_attempt_results.
--   The only deliberate "denormalisation" is the grading-rule snapshot inside
--   quiz_attempts (marks_per_question, negative_marks, passing_percentage,
--   total_questions). It is stored on purpose so that editing a quiz later can
--   never change the result of an attempt that was already made.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS quizsphere
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE quizsphere;

-- -----------------------------------------------------------------------------
-- 1. users : students and administrators (one table, role column)
--    Candidate keys: id, username, email.  Primary key: id.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    username        VARCHAR(30)   NOT NULL,
    email           VARCHAR(120)  NOT NULL,
    full_name       VARCHAR(100)  NOT NULL,
    password_hash   CHAR(64)      NOT NULL,          -- PBKDF2-HMAC-SHA256, hex
    password_salt   CHAR(32)      NOT NULL,          -- 16 random bytes, hex
    hash_iterations INT UNSIGNED  NOT NULL,          -- stored so it can be raised later
    role            ENUM('student','admin') NOT NULL DEFAULT 'student',
    is_active       TINYINT(1)    NOT NULL DEFAULT 1,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT chk_users_username CHECK (REGEXP_LIKE(username, '^[A-Za-z0-9_]{3,30}$')),
    CONSTRAINT chk_users_email    CHECK (email LIKE '%_@_%._%'),
    CONSTRAINT chk_users_iter     CHECK (hash_iterations >= 100000),
    CONSTRAINT chk_users_active   CHECK (is_active IN (0, 1))
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 2. subjects : OOP, DBMS, COA, Statistics, DSA, POM
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subjects (
    id           INT UNSIGNED NOT NULL AUTO_INCREMENT,
    code         VARCHAR(12)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    description  TEXT         NOT NULL,
    accent_color CHAR(7)      NOT NULL DEFAULT '#4f8cff',   -- used by the UI cards
    is_active    TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_subjects_code UNIQUE (code),
    CONSTRAINT uq_subjects_name UNIQUE (name),
    CONSTRAINT chk_subjects_color  CHECK (REGEXP_LIKE(accent_color, '^#[0-9A-Fa-f]{6}$')),
    CONSTRAINT chk_subjects_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 3. topics : subject -> topics (one-to-many)
--    UNIQUE (id, subject_id) exists only so other tables can use a composite
--    foreign key that proves "this topic really belongs to this subject".
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS topics (
    id         INT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_id INT UNSIGNED NOT NULL,
    name       VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_topic_name_per_subject UNIQUE (subject_id, name),
    CONSTRAINT uq_topic_id_subject       UNIQUE (id, subject_id),
    CONSTRAINT fk_topics_subject FOREIGN KEY (subject_id)
        REFERENCES subjects (id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 4. quizzes : configuration of a quiz
--    Number of questions = COUNT(*) in quiz_questions (not stored: no redundancy).
--    Maximum marks      = number of questions x marks_per_question (derived).
--    negative_marks = 0 means "negative marking disabled".
--    max_attempts NULL  means "unlimited attempts".
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quizzes (
    id                 INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    subject_id         INT UNSIGNED  NOT NULL,
    topic_id           INT UNSIGNED  NULL,
    title              VARCHAR(150)  NOT NULL,
    description        TEXT          NOT NULL,
    difficulty         ENUM('easy','medium','hard') NOT NULL,
    duration_minutes   SMALLINT UNSIGNED NOT NULL,
    marks_per_question DECIMAL(5,2)  NOT NULL DEFAULT 1.00,
    negative_marks     DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    passing_percentage DECIMAL(5,2)  NOT NULL DEFAULT 40.00,
    max_attempts       TINYINT UNSIGNED NULL,
    allow_review       TINYINT(1)    NOT NULL DEFAULT 1,
    is_active          TINYINT(1)    NOT NULL DEFAULT 1,
    created_by         INT UNSIGNED  NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_quiz_title_per_subject UNIQUE (subject_id, title),
    KEY idx_quizzes_subject_active (subject_id, is_active),
    CONSTRAINT fk_quizzes_subject FOREIGN KEY (subject_id)
        REFERENCES subjects (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    -- composite FK: the chosen topic must belong to the SAME subject as the quiz
    CONSTRAINT fk_quizzes_topic FOREIGN KEY (topic_id, subject_id)
        REFERENCES topics (id, subject_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_quizzes_creator FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_quiz_duration  CHECK (duration_minutes BETWEEN 1 AND 300),
    CONSTRAINT chk_quiz_marks     CHECK (marks_per_question > 0),
    CONSTRAINT chk_quiz_negative  CHECK (negative_marks >= 0 AND negative_marks <= marks_per_question),
    CONSTRAINT chk_quiz_passing   CHECK (passing_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_quiz_attempts  CHECK (max_attempts IS NULL OR max_attempts >= 1),
    CONSTRAINT chk_quiz_flags     CHECK (allow_review IN (0, 1) AND is_active IN (0, 1))
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 5. questions : the question bank (belongs to a topic, hence to a subject)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS questions (
    id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    topic_id      INT UNSIGNED NOT NULL,
    question_text TEXT         NOT NULL,
    difficulty    ENUM('easy','medium','hard') NOT NULL,
    explanation   TEXT         NOT NULL,
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    created_by    INT UNSIGNED NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_questions_topic_difficulty (topic_id, difficulty),
    CONSTRAINT fk_questions_topic FOREIGN KEY (topic_id)
        REFERENCES topics (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_questions_creator FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_questions_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 6. question_options : exactly four options per question (A-D)
--    "Exactly one correct option" is enforced by the generated column
--    correct_marker + UNIQUE index: only one row per question can hold a
--    non-NULL marker, so a second correct option is rejected by MySQL itself.
--    "Exactly four options" is checked in the Java service layer.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_options (
    id             INT UNSIGNED NOT NULL AUTO_INCREMENT,
    question_id    INT UNSIGNED NOT NULL,
    option_label   CHAR(1)      NOT NULL,
    option_text    VARCHAR(500) NOT NULL,
    is_correct     TINYINT(1)   NOT NULL DEFAULT 0,
    correct_marker INT UNSIGNED
        GENERATED ALWAYS AS (IF(is_correct = 1, question_id, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    CONSTRAINT uq_option_label       UNIQUE (question_id, option_label),
    CONSTRAINT uq_option_id_question UNIQUE (id, question_id),   -- target of composite FK
    CONSTRAINT uq_one_correct_option UNIQUE (correct_marker),
    CONSTRAINT fk_options_question FOREIGN KEY (question_id)
        REFERENCES questions (id) ON DELETE CASCADE,
    CONSTRAINT chk_option_label   CHECK (option_label IN ('A','B','C','D')),
    CONSTRAINT chk_option_correct CHECK (is_correct IN (0, 1))
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 7. quiz_questions : many-to-many link between quizzes and the question bank
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_questions (
    quiz_id     INT UNSIGNED      NOT NULL,
    question_id INT UNSIGNED      NOT NULL,
    position    SMALLINT UNSIGNED NOT NULL,
    PRIMARY KEY (quiz_id, question_id),
    CONSTRAINT uq_quiz_position UNIQUE (quiz_id, position),
    KEY idx_quiz_questions_question (question_id),
    CONSTRAINT fk_qq_quiz FOREIGN KEY (quiz_id)
        REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT fk_qq_question FOREIGN KEY (question_id)
        REFERENCES questions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_qq_position CHECK (position >= 1)
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 8. quiz_attempts : one row per attempt (history is never overwritten)
--    The four snapshot columns freeze the grading rules at start time.
--    open_key + UNIQUE index => a student can have at most ONE in-progress
--    attempt per quiz, so double-clicking "Start" cannot create two attempts.
--    score / passed / submitted_at stay NULL until the attempt is submitted.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_attempts (
    id                 INT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            INT UNSIGNED NOT NULL,
    quiz_id            INT UNSIGNED NOT NULL,
    status             ENUM('in_progress','submitted') NOT NULL DEFAULT 'in_progress',
    started_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deadline_at        DATETIME     NOT NULL,
    submitted_at       DATETIME     NULL,
    auto_submitted     TINYINT(1)   NOT NULL DEFAULT 0,
    -- grading-rule snapshot
    total_questions    SMALLINT UNSIGNED NOT NULL,
    marks_per_question DECIMAL(5,2) NOT NULL,
    negative_marks     DECIMAL(5,2) NOT NULL,
    passing_percentage DECIMAL(5,2) NOT NULL,
    -- result (filled by the server-side grader)
    score              DECIMAL(7,2) NULL,
    passed             TINYINT(1)   NULL,
    open_key           TINYINT
        GENERATED ALWAYS AS (IF(status = 'in_progress', 1, NULL)) VIRTUAL,
    PRIMARY KEY (id),
    CONSTRAINT uq_one_open_attempt UNIQUE (user_id, quiz_id, open_key),
    KEY idx_attempts_user_started (user_id, started_at),
    KEY idx_attempts_quiz_status  (quiz_id, status),
    CONSTRAINT fk_attempts_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attempts_quiz FOREIGN KEY (quiz_id)
        REFERENCES quizzes (id) ON DELETE RESTRICT,
    CONSTRAINT chk_attempt_deadline CHECK (deadline_at > started_at),
    CONSTRAINT chk_attempt_snapshot CHECK (
        total_questions >= 1 AND marks_per_question > 0 AND negative_marks >= 0
        AND passing_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_attempt_state CHECK (
        (status = 'in_progress' AND submitted_at IS NULL AND score IS NULL AND passed IS NULL)
        OR
        (status = 'submitted' AND submitted_at IS NOT NULL AND score IS NOT NULL AND passed IS NOT NULL)),
    CONSTRAINT chk_attempt_flags CHECK (auto_submitted IN (0, 1))
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 9. attempt_answers : one row per (attempt, question), created when the
--    attempt starts. This also freezes WHICH questions belong to the attempt.
--    selected_option_id NULL = unanswered.
--    The composite FK (selected_option_id, question_id) guarantees the chosen
--    option belongs to that very question.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attempt_answers (
    attempt_id         INT UNSIGNED      NOT NULL,
    question_id        INT UNSIGNED      NOT NULL,
    position           SMALLINT UNSIGNED NOT NULL,
    selected_option_id INT UNSIGNED      NULL,
    marked_for_review  TINYINT(1)        NOT NULL DEFAULT 0,
    answered_at        DATETIME          NULL,
    is_correct         TINYINT(1)        NULL,     -- set by the grader
    marks_awarded      DECIMAL(5,2)      NULL,     -- set by the grader
    PRIMARY KEY (attempt_id, question_id),
    CONSTRAINT uq_attempt_position UNIQUE (attempt_id, position),
    KEY idx_answers_question (question_id),
    CONSTRAINT fk_answers_attempt FOREIGN KEY (attempt_id)
        REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id)
        REFERENCES questions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_answers_option FOREIGN KEY (selected_option_id, question_id)
        REFERENCES question_options (id, question_id) ON DELETE RESTRICT,
    CONSTRAINT chk_answer_review  CHECK (marked_for_review IN (0, 1)),
    CONSTRAINT chk_answer_correct CHECK (is_correct IS NULL OR is_correct IN (0, 1))
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 10. sessions : server-side login sessions.
--     Only the SHA-256 hash of the cookie token is stored, so a leaked
--     database dump cannot be used to hijack a live session.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sessions (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_hash   CHAR(64)    NOT NULL,
    csrf_token   CHAR(64)    NOT NULL,
    user_id      INT UNSIGNED NOT NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   DATETIME    NOT NULL,
    ip_address   VARCHAR(45) NULL,
    user_agent   VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_sessions_token UNIQUE (token_hash),
    KEY idx_sessions_user (user_id),
    KEY idx_sessions_expiry (expires_at),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 11. login_attempts : used for login throttling (brute-force protection).
--     No foreign key on purpose: failed logins for unknown usernames must be
--     recorded too.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS login_attempts (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    identifier   VARCHAR(120) NOT NULL,
    ip_address   VARCHAR(45)  NOT NULL,
    success      TINYINT(1)   NOT NULL,
    attempted_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_login_identifier (identifier, attempted_at),
    KEY idx_login_ip (ip_address, attempted_at)
) ENGINE=InnoDB;

-- =============================================================================
-- VIEWS (stored SELECT statements that the Java reports reuse)
-- =============================================================================

-- Every submitted attempt with subject/quiz details and derived columns.
CREATE OR REPLACE VIEW v_attempt_results AS
SELECT  a.id                                   AS attempt_id,
        a.user_id,
        a.quiz_id,
        q.title                                AS quiz_title,
        q.difficulty,
        s.id                                   AS subject_id,
        s.code                                 AS subject_code,
        s.name                                 AS subject_name,
        a.started_at,
        a.submitted_at,
        a.auto_submitted,
        a.score,
        (a.total_questions * a.marks_per_question) AS max_score,
        GREATEST(0, ROUND(a.score / (a.total_questions * a.marks_per_question) * 100, 2))
                                               AS percentage,
        a.passed,
        TIMESTAMPDIFF(SECOND, a.started_at, a.submitted_at) AS time_taken_seconds
FROM    quiz_attempts a
JOIN    quizzes  q ON q.id = a.quiz_id
JOIN    subjects s ON s.id = q.subject_id
WHERE   a.status = 'submitted';

-- Per-quiz statistics for the administrator (LEFT JOIN keeps quizzes with 0 attempts).
CREATE OR REPLACE VIEW v_quiz_stats AS
SELECT  q.id                                   AS quiz_id,
        q.title,
        q.subject_id,
        COUNT(r.attempt_id)                    AS attempts,
        COUNT(DISTINCT r.user_id)              AS students,
        ROUND(AVG(r.percentage), 2)            AS avg_percentage,
        MAX(r.percentage)                      AS best_percentage,
        ROUND(100 * SUM(r.passed) / NULLIF(COUNT(r.attempt_id), 0), 2) AS pass_rate
FROM    quizzes q
LEFT JOIN v_attempt_results r ON r.quiz_id = q.id
GROUP BY q.id, q.title, q.subject_id;

-- Per-student, per-subject statistics (drives dashboard and analytics).
CREATE OR REPLACE VIEW v_student_subject_stats AS
SELECT  r.user_id,
        r.subject_id,
        r.subject_code,
        r.subject_name,
        COUNT(*)                               AS attempts,
        ROUND(AVG(r.percentage), 2)            AS avg_percentage,
        MAX(r.percentage)                      AS best_percentage,
        SUM(r.passed)                          AS passed_count
FROM    v_attempt_results r
GROUP BY r.user_id, r.subject_id, r.subject_code, r.subject_name;

-- Optional leaderboard: shows ONLY the public username, never e-mail or full name.
CREATE OR REPLACE VIEW v_leaderboard AS
SELECT  u.username,
        COUNT(r.attempt_id)                    AS attempts,
        ROUND(AVG(r.percentage), 2)            AS avg_percentage,
        MAX(r.percentage)                      AS best_percentage
FROM    users u
JOIN    v_attempt_results r ON r.user_id = u.id
WHERE   u.role = 'student' AND u.is_active = 1
GROUP BY u.id, u.username;
