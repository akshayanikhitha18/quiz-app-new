package com.quizsphere;

import com.quizsphere.Errors.Conflict;
import com.quizsphere.Errors.NotFound;
import com.quizsphere.Errors.PermissionDenied;
import com.quizsphere.Errors.ValidationError;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The quiz engine. Rules that live HERE (never in JavaScript):
 *  - attempt limits, deadlines (+ small grace period) and duplicate-submit protection
 *  - grading (via a QuizGrader) and persistence inside ONE transaction
 *  - ownership checks: every attempt query is filtered by the session's user id
 */
public class QuizService {
    private final DatabaseManager db;
    private final QuizGrader grader;
    private final Config config;

    public QuizService(DatabaseManager db, QuizGrader grader, Config config) {
        this.db = db;
        this.grader = grader;
        this.config = config;
    }

    // ------------------------------------------------------------------ start
    public Map<String, Object> startAttempt(long userId, long quizId) {
        finalizeExpired(userId, quizId);
        Row row = db.fetchOne("SELECT q.* FROM quizzes q JOIN subjects s ON s.id = q.subject_id "
                + "WHERE q.id = ? AND q.is_active = 1 AND s.is_active = 1", quizId);
        if (row == null) throw new NotFound("Quiz not found.");
        Quiz quiz = new Quiz(row);

        Row open = openAttempt(userId, quizId);
        if (open != null) return started(open.getLong("id"), true);

        long used = db.fetchOne("SELECT COUNT(*) AS n FROM quiz_attempts WHERE user_id = ? AND quiz_id = ?", userId, quizId).getLong("n");
        quiz.ensureCanStart(used);
        LocalDateTime now = DatabaseManager.utcNow();
        try {
            long attemptId = db.inTransaction(tx -> {      // attempt row + all answer rows, or nothing
                long n = tx.fetchOne("SELECT COUNT(*) AS n FROM quiz_questions qq JOIN questions q ON q.id = qq.question_id "
                        + "WHERE qq.quiz_id = ? AND q.is_active = 1", quizId).getLong("n");
                if (n == 0) throw new Conflict("This quiz has no questions yet.");
                long id = tx.execute("INSERT INTO quiz_attempts (user_id, quiz_id, started_at, deadline_at, total_questions, "
                                + "marks_per_question, negative_marks, passing_percentage) VALUES (?,?,?,?,?,?,?,?)",
                        userId, quizId, now, now.plusSeconds(quiz.timeLimitSeconds()), n,
                        quiz.getMarksPerQuestion(), quiz.getNegativeMarks(), quiz.getPassingPercentage()).lastRowId();
                tx.execute("INSERT INTO attempt_answers (attempt_id, question_id, position) "
                        + "SELECT ?, qq.question_id, ROW_NUMBER() OVER (ORDER BY qq.position) FROM quiz_questions qq "
                        + "JOIN questions q ON q.id = qq.question_id WHERE qq.quiz_id = ? AND q.is_active = 1", id, quizId);
                return id;
            });
            return started(attemptId, false);
        } catch (DatabaseException.ConstraintViolation e) {
            if ("duplicate".equals(e.kind)) {              // double click: the unique index blocked the 2nd attempt
                Row again = openAttempt(userId, quizId);
                if (again != null) return started(again.getLong("id"), true);
            }
            throw e;
        }
    }

    private static Map<String, Object> started(long attemptId, boolean resumed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("attempt_id", attemptId);
        m.put("resumed", resumed);
        return m;
    }

    private Row openAttempt(long userId, long quizId) {
        return db.fetchOne("SELECT id FROM quiz_attempts WHERE user_id = ? AND quiz_id = ? AND status = 'in_progress'", userId, quizId);
    }

    private QuizAttempt loadAttempt(QueryInterface runner, long userId, long attemptId, boolean lock) {
        Row row = runner.fetchOne("SELECT * FROM quiz_attempts WHERE id = ? AND user_id = ?" + (lock ? " FOR UPDATE" : ""), attemptId, userId);
        if (row == null) throw new NotFound("Attempt not found.");
        QuizAttempt attempt = QuizAttempt.fromRow(row);
        attempt.ensureOwnedBy(userId);
        return attempt;
    }

    // -------------------------------------------------------------- questions
    public Map<String, Object> getQuestions(long userId, long attemptId) {
        QuizAttempt attempt = loadAttempt(db, userId, attemptId, false);
        LocalDateTime now = DatabaseManager.utcNow();
        if (attempt.isSubmitted()) throw new Conflict("This attempt has already been submitted.", "already_submitted");
        if (!attempt.canAcceptAnswers(now, config.deadlineGraceSeconds())) {
            submit(userId, attemptId, null, true);
            throw new Conflict("Time is up. Your attempt was submitted automatically.", "expired");
        }
        // NOTE: is_correct is never selected here, so the answer key cannot leak to the browser.
        List<Row> rows = db.fetchAll("SELECT aa.question_id, aa.position, aa.selected_option_id, aa.marked_for_review, q.question_text "
                + "FROM attempt_answers aa JOIN questions q ON q.id = aa.question_id WHERE aa.attempt_id = ? ORDER BY aa.position", attemptId);
        List<Row> opts = db.fetchAll("SELECT o.id, o.question_id, o.option_label, o.option_text FROM question_options o "
                + "JOIN attempt_answers aa ON aa.question_id = o.question_id WHERE aa.attempt_id = ? "
                + "ORDER BY o.question_id, o.option_label", attemptId);
        Map<Long, List<Option>> byQuestion = new LinkedHashMap<>();
        for (Row o : opts) {
            byQuestion.computeIfAbsent(o.getLong("question_id"), k -> new ArrayList<>())
                    .add(new Option(o.getLong("id"), o.getStr("option_label"), o.getStr("option_text"), false));
        }
        List<Object> questions = new ArrayList<>();
        for (Row r : rows) {
            questions.add(new Question(r.getLong("question_id"), r.getInt("position"), r.getStr("question_text"),
                    byQuestion.getOrDefault(r.getLong("question_id"), List.of()), r.getLong("selected_option_id"),
                    r.isTrue("marked_for_review"), null).toPublicDict());
        }
        Row meta = db.fetchOne("SELECT q.title, q.duration_minutes, s.code AS subject_code, s.name AS subject_name "
                + "FROM quiz_attempts a JOIN quizzes q ON q.id = a.quiz_id JOIN subjects s ON s.id = q.subject_id WHERE a.id = ?", attemptId);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", attemptId);
        info.put("quiz_title", meta.getStr("title"));
        info.put("subject_code", meta.getStr("subject_code"));
        info.put("subject_name", meta.getStr("subject_name"));
        info.put("total", questions.size());
        info.put("duration_minutes", meta.getInt("duration_minutes"));
        info.put("seconds_remaining", attempt.secondsRemaining(now));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("attempt", info);
        out.put("questions", questions);
        return out;
    }

    // ---------------------------------------------------------------- answers
    public Map<String, Object> saveAnswer(long userId, long attemptId, Map<String, Object> data) {
        int questionId = Validators.asInt(data.get("question_id"), "question_id", 1);
        Integer optionId = Validators.asInt(data.get("selected_option_id"), "selected_option_id", 1, null, true);
        boolean marked = Validators.asBool(data.get("marked_for_review"));
        LocalDateTime now = DatabaseManager.utcNow();
        int remaining = db.inTransaction(tx -> {
            QuizAttempt attempt = loadAttempt(tx, userId, attemptId, true);        // row lock: serialises with submit()
            requireOpen(attempt, now);
            applyAnswer(tx, attemptId, questionId, optionId, marked, now);
            return attempt.secondsRemaining(now);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", true);
        out.put("seconds_remaining", remaining);
        return out;
    }

    private void requireOpen(QuizAttempt attempt, LocalDateTime now) {
        if (attempt.isSubmitted()) throw new Conflict("This attempt has already been submitted.", "already_submitted");
        if (!attempt.canAcceptAnswers(now, config.deadlineGraceSeconds())) throw new Conflict("Time is up; answers are closed.", "closed");
    }

    private static void applyAnswer(QueryInterface runner, long attemptId, long questionId, Integer optionId, boolean marked, LocalDateTime now) {
        if (runner.fetchOne("SELECT 1 AS x FROM attempt_answers WHERE attempt_id = ? AND question_id = ?", attemptId, questionId) == null) {
            throw new ValidationError("That question is not part of this attempt.");
        }
        if (optionId != null && runner.fetchOne("SELECT 1 AS x FROM question_options WHERE id = ? AND question_id = ?", optionId, questionId) == null) {
            throw new ValidationError("Invalid answer option.");
        }
        runner.execute("UPDATE attempt_answers SET selected_option_id = ?, marked_for_review = ?, answered_at = ? "
                + "WHERE attempt_id = ? AND question_id = ?", optionId, marked ? 1 : 0, optionId == null ? null : now, attemptId, questionId);
    }

    // ----------------------------------------------------------------- submit
    /** Grade and store an attempt - atomically, and only once. answers may be null. */
    public Row submit(long userId, long attemptId, List<Object> answers, boolean auto) {
        LocalDateTime now = DatabaseManager.utcNow();
        db.runInTransaction(tx -> {
            QuizAttempt attempt = loadAttempt(tx, userId, attemptId, true);        // row lock: one submit at a time
            if (attempt.isSubmitted()) throw new Conflict("This attempt has already been submitted.", "already_submitted");
            if (answers != null && attempt.canAcceptAnswers(now, config.deadlineGraceSeconds())) {
                int count = 0;
                for (Object o : answers) {                // last-minute answers sent with the submit request
                    if (++count > 500) break;
                    if (o instanceof Map<?, ?> raw) {
                        @SuppressWarnings("unchecked") Map<String, Object> a = (Map<String, Object>) raw;
                        applyAnswer(tx, attemptId, Validators.asInt(a.get("question_id"), "question_id", 1),
                                Validators.asInt(a.get("selected_option_id"), "selected_option_id", 1, null, true),
                                Validators.asBool(a.get("marked_for_review")), now);
                    }
                }
            }
            List<Row> rows = tx.fetchAll("SELECT aa.question_id, aa.selected_option_id, "
                    + "(SELECT o.id FROM question_options o WHERE o.question_id = aa.question_id AND o.is_correct = 1) AS correct_option_id "
                    + "FROM attempt_answers aa WHERE aa.attempt_id = ?", attemptId);
            GradeResult result = grader.grade(attempt.getRules(), rows);
            for (AnswerGrade g : result.answers()) {
                tx.execute("UPDATE attempt_answers SET is_correct = ?, marks_awarded = ? WHERE attempt_id = ? AND question_id = ?",
                        g.isCorrect() == null ? null : (g.isCorrect() ? 1 : 0), g.marks(), attemptId, g.questionId());
            }
            QuizAttempt.SubmitValues v = attempt.submit(result, now, auto);        // the object enforces the state change
            int changed = tx.execute("UPDATE quiz_attempts SET status = 'submitted', submitted_at = ?, auto_submitted = ?, "
                            + "score = ?, passed = ? WHERE id = ? AND status = 'in_progress'",
                    v.submittedAt(), v.autoSubmitted(), v.score(), v.passed(), attemptId).rowCount();
            if (changed != 1) throw new Conflict("This attempt has already been submitted.", "already_submitted");
        });
        return getResult(userId, attemptId);
    }

    /** Auto-submit this student's attempts whose deadline (+ grace) has passed. quizId may be null. */
    public void finalizeExpired(long userId, Long quizId) {
        LocalDateTime cutoff = DatabaseManager.utcNow().minusSeconds(config.deadlineGraceSeconds());
        StringBuilder sql = new StringBuilder("SELECT id FROM quiz_attempts WHERE user_id = ? AND status = 'in_progress' AND deadline_at < ?");
        List<Object> params = new ArrayList<>(List.of(userId, cutoff));
        if (quizId != null) {
            sql.append(" AND quiz_id = ?");
            params.add(quizId);
        }
        for (Row r : db.fetchAll(sql.toString(), params)) {
            try {
                submit(userId, r.getLong("id"), null, true);
            } catch (Conflict ignored) {
                // already submitted by another request: nothing to do
            }
        }
    }

    public void finalizeExpired(long userId) { finalizeExpired(userId, null); }

    // ---------------------------------------------------------------- results
    public Row getResult(long userId, long attemptId) {
        Row row = db.fetchOne("SELECT r.*, q.allow_review FROM v_attempt_results r JOIN quizzes q ON q.id = r.quiz_id "
                + "WHERE r.attempt_id = ? AND r.user_id = ?", attemptId, userId);
        if (row == null) {
            Row state = db.fetchOne("SELECT status FROM quiz_attempts WHERE id = ? AND user_id = ?", attemptId, userId);
            if (state == null) throw new NotFound("Attempt not found.");
            throw new Conflict("This attempt has not been submitted yet.", "in_progress");
        }
        Row counts = db.fetchOne("SELECT COALESCE(SUM(is_correct = 1), 0) AS correct, COALESCE(SUM(is_correct = 0), 0) AS incorrect, "
                + "COALESCE(SUM(selected_option_id IS NULL), 0) AS unanswered FROM attempt_answers WHERE attempt_id = ?", attemptId);
        row.putAll(counts);
        row.put("feedback", QuizGrader.performanceFeedback(row.getDec("percentage"), row.isTrue("passed")));
        return row;
    }

    public Map<String, Object> getReview(long userId, long attemptId) {
        Row result = getResult(userId, attemptId);        // also proves ownership and that it is submitted
        if (!result.isTrue("allow_review")) throw new PermissionDenied("Answer review is disabled for this quiz.");
        List<Row> rows = db.fetchAll("SELECT aa.question_id, aa.position, aa.selected_option_id, q.question_text, q.explanation "
                + "FROM attempt_answers aa JOIN questions q ON q.id = aa.question_id WHERE aa.attempt_id = ? ORDER BY aa.position", attemptId);
        List<Row> opts = db.fetchAll("SELECT o.id, o.question_id, o.option_label, o.option_text, o.is_correct FROM question_options o "
                + "JOIN attempt_answers aa ON aa.question_id = o.question_id WHERE aa.attempt_id = ? "
                + "ORDER BY o.question_id, o.option_label", attemptId);
        Map<Long, List<Option>> byQuestion = new LinkedHashMap<>();
        for (Row o : opts) {
            byQuestion.computeIfAbsent(o.getLong("question_id"), k -> new ArrayList<>())
                    .add(new Option(o.getLong("id"), o.getStr("option_label"), o.getStr("option_text"), o.isTrue("is_correct")));
        }
        List<Object> questions = new ArrayList<>();
        for (Row r : rows) {
            questions.add(new Question(r.getLong("question_id"), r.getInt("position"), r.getStr("question_text"),
                    byQuestion.getOrDefault(r.getLong("question_id"), List.of()), r.getLong("selected_option_id"),
                    false, r.getStr("explanation")).toReviewDict());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", result);
        out.put("questions", questions);
        return out;
    }
}
