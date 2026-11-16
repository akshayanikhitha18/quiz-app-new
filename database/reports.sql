-- Sample reporting queries used by QuizSphere (replace @uid with a real user id).
USE quizsphere;
-- Student dashboard totals (aggregate functions on a view that JOINs attempts, quizzes, subjects)
SELECT COUNT(*) AS attempted, ROUND(AVG(percentage),2) AS avg_pct, MAX(percentage) AS best_pct, SUM(passed) AS passed
FROM v_attempt_results WHERE user_id = @uid;
-- Quiz history, newest first
SELECT quiz_title, subject_code, submitted_at, score, percentage, passed, time_taken_seconds
FROM v_attempt_results WHERE user_id = @uid ORDER BY submitted_at DESC;
-- GROUP BY + HAVING: subjects where the student averages below 60 %
SELECT subject_name, AVG(percentage) AS avg_pct FROM v_attempt_results
WHERE user_id = @uid GROUP BY subject_name HAVING AVG(percentage) < 60;
-- Subquery: quizzes the student has never attempted
SELECT q.id, q.title FROM quizzes q
WHERE q.is_active = 1 AND q.id NOT IN (SELECT quiz_id FROM quiz_attempts WHERE user_id = @uid);
-- Leaderboard (public usernames only)
SELECT username, attempts, avg_percentage FROM v_leaderboard ORDER BY avg_percentage DESC LIMIT 10;
-- Administrator: per-subject performance (LEFT JOIN keeps subjects with no attempts)
SELECT s.code, COUNT(r.attempt_id) AS attempts, ROUND(AVG(r.percentage),2) AS avg_pct
FROM subjects s LEFT JOIN v_attempt_results r ON r.subject_id = s.id GROUP BY s.id, s.code;
-- Administrator: hardest quizzes
SELECT title, attempts, avg_percentage FROM v_quiz_stats WHERE attempts > 0 ORDER BY avg_percentage ASC LIMIT 5;
-- Transaction demo (ACID): both statements succeed together or neither does
START TRANSACTION;
UPDATE quizzes SET is_active = 0 WHERE id = 3;
UPDATE quizzes SET is_active = 1 WHERE id = 3;
ROLLBACK;
