package com.quizsphere;

import com.quizsphere.Errors.NotFound;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Student-facing catalogue: subjects and quizzes, decorated with the student's own progress. */
public class CatalogService {
    public static final String QUIZ_SELECT =
            "SELECT q.id, q.title, q.description, q.difficulty, q.duration_minutes, q.marks_per_question, "
            + "q.negative_marks, q.passing_percentage, q.max_attempts, q.allow_review, q.is_active, "
            + "q.subject_id, q.topic_id, s.code AS subject_code, s.name AS subject_name, t.name AS topic_name, "
            + "(SELECT COUNT(*) FROM quiz_questions qq JOIN questions x ON x.id = qq.question_id "
            + "  WHERE qq.quiz_id = q.id AND x.is_active = 1) AS question_count "
            + "FROM quizzes q JOIN subjects s ON s.id = q.subject_id LEFT JOIN topics t ON t.id = q.topic_id ";

    private final QueryInterface db;

    public CatalogService(QueryInterface db) { this.db = db; }

    public List<Row> publicSubjects() {
        return db.fetchAll("SELECT id, code, name, description, accent_color FROM subjects WHERE is_active = 1 ORDER BY id");
    }

    public List<Row> listSubjects(long userId) {
        return db.fetchAll(
                "SELECT s.id, s.code, s.name, s.description, s.accent_color, "
                + "(SELECT COUNT(*) FROM quizzes q WHERE q.subject_id = s.id AND q.is_active = 1) AS quiz_count, "
                + "(SELECT COUNT(DISTINCT qq.question_id) FROM quiz_questions qq JOIN quizzes q ON q.id = qq.quiz_id "
                + "   JOIN questions x ON x.id = qq.question_id "
                + "  WHERE q.subject_id = s.id AND q.is_active = 1 AND x.is_active = 1) AS question_count, "
                + "(SELECT COUNT(DISTINCT r.quiz_id) FROM v_attempt_results r WHERE r.user_id = ? AND r.subject_id = s.id) AS quizzes_attempted, "
                + "(SELECT ROUND(AVG(r.percentage), 2) FROM v_attempt_results r WHERE r.user_id = ? AND r.subject_id = s.id) AS avg_percentage "
                + "FROM subjects s WHERE s.is_active = 1 ORDER BY s.id", userId, userId);
    }

    public Map<String, Object> getSubject(long subjectId, long userId) {
        Row row = db.fetchOne("SELECT id, code, name, description, accent_color FROM subjects WHERE id = ? AND is_active = 1", subjectId);
        if (row == null) throw new NotFound("Subject not found.");
        List<Object> topics = new ArrayList<>();
        for (Row t : db.fetchAll("SELECT name FROM topics WHERE subject_id = ? ORDER BY id", subjectId)) topics.add(t.getStr("name"));
        Map<String, Object> out = new LinkedHashMap<>(Subject.fromRow(row).toDict());
        out.put("topics", topics);
        out.put("quizzes", listQuizzes(userId, subjectId));
        return out;
    }

    public List<Row> listQuizzes(long userId, Long subjectId) {
        StringBuilder sql = new StringBuilder(QUIZ_SELECT + "WHERE q.is_active = 1 AND s.is_active = 1");
        List<Object> params = new ArrayList<>();
        if (subjectId != null) {
            sql.append(" AND q.subject_id = ?");
            params.add(subjectId);
        }
        sql.append(" ORDER BY q.subject_id, FIELD(q.difficulty, 'easy', 'medium', 'hard'), q.id");
        return decorate(db.fetchAll(sql.toString(), params), userId);
    }

    public Row getQuiz(long quizId, long userId) {
        Row row = db.fetchOne(QUIZ_SELECT + "WHERE q.id = ? AND q.is_active = 1 AND s.is_active = 1", quizId);
        if (row == null) throw new NotFound("Quiz not found.");
        return decorate(new ArrayList<>(List.of(row)), userId).get(0);
    }

    /** Add this student's own attempt info to each quiz row. */
    private List<Row> decorate(List<Row> quizzes, long userId) {
        Map<Long, Long> used = new HashMap<>();
        for (Row r : db.fetchAll("SELECT quiz_id, COUNT(*) AS n FROM quiz_attempts WHERE user_id = ? GROUP BY quiz_id", userId)) {
            used.put(r.getLong("quiz_id"), r.getLong("n"));
        }
        Map<Long, Row> done = new HashMap<>();
        for (Row r : db.fetchAll("SELECT quiz_id, COUNT(*) AS n, MAX(percentage) AS best FROM v_attempt_results "
                + "WHERE user_id = ? GROUP BY quiz_id", userId)) {
            done.put(r.getLong("quiz_id"), r);
        }
        Map<Long, Long> open = new HashMap<>();
        for (Row r : db.fetchAll("SELECT quiz_id, id FROM quiz_attempts WHERE user_id = ? AND status = 'in_progress' "
                + "AND deadline_at > ?", userId, DatabaseManager.utcNow())) {
            open.put(r.getLong("quiz_id"), r.getLong("id"));
        }
        for (Row q : quizzes) {
            long quizId = q.getLong("id");
            long n = used.getOrDefault(quizId, 0L);
            Integer maxAttempts = q.getInt("max_attempts");
            Integer left = maxAttempts == null ? null : (int) Math.max(0, maxAttempts - n);
            int questionCount = q.getInt("question_count");
            q.put("attempts_used", n);
            q.put("attempted", done.containsKey(quizId));
            q.put("best_percentage", done.containsKey(quizId) ? done.get(quizId).get("best") : null);
            q.put("in_progress_attempt_id", open.get(quizId));
            q.put("attempts_left", left);
            q.put("can_start", questionCount > 0 && (left == null || left != 0 || open.containsKey(quizId)));
            q.put("max_score", q.getDec("marks_per_question").multiply(BigDecimal.valueOf(questionCount)));
        }
        return quizzes;
    }
}
