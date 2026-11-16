package com.quizsphere;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reports about ONE student. Every query is filtered by userId, which the server takes from the session. */
public class StudentReport extends ReportGenerator {
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "date", "submitted_at", "score", "score", "percentage", "percentage", "title", "quiz_title");

    public StudentReport(QueryInterface db) { super(db); }

    @Override
    public Map<String, Object> dashboard(Long userId) {
        Row stats = db.fetchOne(
                "SELECT COUNT(*) AS attempted, ROUND(AVG(percentage), 2) AS avg_percentage, "
                + "MAX(percentage) AS best_percentage, COALESCE(SUM(passed), 0) AS passed_count "
                + "FROM v_attempt_results WHERE user_id = ?", userId);
        List<Row> recent = db.fetchAll(
                "SELECT attempt_id, quiz_id, quiz_title, subject_code, score, max_score, percentage, passed, submitted_at "
                + "FROM v_attempt_results WHERE user_id = ? ORDER BY submitted_at DESC, attempt_id DESC LIMIT 5", userId);
        List<Row> subjects = db.fetchAll(
                "SELECT s.id, s.code, s.name, s.accent_color, "
                + "(SELECT COUNT(*) FROM quizzes q WHERE q.subject_id = s.id AND q.is_active = 1) AS quiz_count, "
                + "(SELECT COUNT(DISTINCT r.quiz_id) FROM v_attempt_results r WHERE r.user_id = ? AND r.subject_id = s.id) AS quizzes_attempted, "
                + "(SELECT ROUND(AVG(r.percentage), 2) FROM v_attempt_results r WHERE r.user_id = ? AND r.subject_id = s.id) AS avg_percentage "
                + "FROM subjects s WHERE s.is_active = 1 ORDER BY s.id", userId, userId);
        List<Row> recommended = db.fetchAll(
                "SELECT q.id, q.title, q.difficulty, q.duration_minutes, s.code AS subject_code, "
                + "(SELECT COUNT(*) FROM quiz_questions qq JOIN questions x ON x.id = qq.question_id "
                + "  WHERE qq.quiz_id = q.id AND x.is_active = 1) AS question_count "
                + "FROM quizzes q JOIN subjects s ON s.id = q.subject_id "
                + "WHERE q.is_active = 1 AND s.is_active = 1 "
                + "AND q.id NOT IN (SELECT quiz_id FROM quiz_attempts WHERE user_id = ?) "
                + "ORDER BY FIELD(q.difficulty, 'easy', 'medium', 'hard'), q.id LIMIT 4", userId);
        List<Row> inProgress = db.fetchAll(
                "SELECT a.id AS attempt_id, q.title, s.code AS subject_code, a.deadline_at "
                + "FROM quiz_attempts a JOIN quizzes q ON q.id = a.quiz_id JOIN subjects s ON s.id = q.subject_id "
                + "WHERE a.user_id = ? AND a.status = 'in_progress' ORDER BY a.started_at DESC", userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stats", stats);
        out.put("recent", recent);
        out.put("subjects", subjects);
        out.put("recommended", recommended);
        out.put("in_progress", inProgress);
        return out;
    }

    public List<Row> history(long userId, Integer subjectId, String status, String sort, String direction) {
        StringBuilder sql = new StringBuilder(
                "SELECT attempt_id, quiz_id, quiz_title, subject_id, subject_code, subject_name, difficulty, "
                + "submitted_at, auto_submitted, score, max_score, percentage, passed, time_taken_seconds "
                + "FROM v_attempt_results WHERE user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (subjectId != null && subjectId > 0) {
            sql.append(" AND subject_id = ?");
            params.add(subjectId);
        }
        if ("passed".equals(status)) sql.append(" AND passed = 1");
        else if ("failed".equals(status)) sql.append(" AND passed = 0");
        // ORDER BY cannot use placeholders, so only whitelisted constants are inserted here.
        String column = SORT_COLUMNS.getOrDefault(sort, "submitted_at");
        String order = "asc".equals(direction) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(column).append(' ').append(order).append(", attempt_id DESC");
        return db.fetchAll(sql.toString(), params);
    }

    @Override
    public Map<String, Object> analytics(Long userId) {
        List<Row> subjects = db.fetchAll(
                "SELECT subject_id, subject_code, subject_name, attempts, avg_percentage, best_percentage, passed_count "
                + "FROM v_student_subject_stats WHERE user_id = ? ORDER BY avg_percentage DESC", userId);
        List<Row> timeline = db.fetchAll(
                "SELECT attempt_id, quiz_id, quiz_title, subject_code, percentage, passed, submitted_at "
                + "FROM v_attempt_results WHERE user_id = ? ORDER BY submitted_at, attempt_id", userId);
        Row overall = db.fetchOne(
                "SELECT COUNT(*) AS attempts, ROUND(AVG(percentage), 2) AS avg_percentage, "
                + "COALESCE(ROUND(100 * SUM(passed) / NULLIF(COUNT(*), 0), 2), 0) AS pass_rate "
                + "FROM v_attempt_results WHERE user_id = ?", userId);

        Map<Long, List<Row>> perQuiz = new LinkedHashMap<>();
        for (Row r : timeline) perQuiz.computeIfAbsent(r.getLong("quiz_id"), k -> new ArrayList<>()).add(r);
        List<Map<String, Object>> improvement = new ArrayList<>();
        for (List<Row> rows : perQuiz.values()) {
            if (rows.size() < 2) continue;
            double first = rows.get(0).getDec("percentage").doubleValue();
            double latest = rows.get(rows.size() - 1).getDec("percentage").doubleValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quiz_title", rows.get(0).getStr("quiz_title"));
            m.put("attempts", rows.size());
            m.put("first", first);
            m.put("latest", latest);
            m.put("change", Math.round((latest - first) * 100) / 100.0);
            improvement.add(m);
        }
        Double lastChange = null;
        if (timeline.size() >= 2) {
            double a = timeline.get(timeline.size() - 1).getDec("percentage").doubleValue();
            double b = timeline.get(timeline.size() - 2).getDec("percentage").doubleValue();
            lastChange = Math.round((a - b) * 100) / 100.0;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("overall", overall);
        out.put("subjects", subjects);
        out.put("timeline", timeline);
        out.put("best_subject", subjects.isEmpty() ? null : subjects.get(0));
        out.put("weakest_subject", subjects.size() > 1 ? subjects.get(subjects.size() - 1) : null);
        out.put("improvement", improvement);
        out.put("last_change", lastChange);
        return out;
    }
}
