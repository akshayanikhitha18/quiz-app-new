package com.quizsphere;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregate reports across ALL students (administrator only). */
public class AdminReport extends ReportGenerator {
    public AdminReport(QueryInterface db) { super(db); }

    @Override
    public Map<String, Object> dashboard(Long userId) {
        Row totals = db.fetchOne(
                "SELECT (SELECT COUNT(*) FROM users WHERE role = 'student') AS students, "
                + "(SELECT COUNT(*) FROM subjects WHERE is_active = 1) AS subjects, "
                + "(SELECT COUNT(*) FROM quizzes) AS quizzes, "
                + "(SELECT COUNT(*) FROM quizzes WHERE is_active = 1) AS active_quizzes, "
                + "(SELECT COUNT(*) FROM questions) AS questions, "
                + "(SELECT COUNT(*) FROM v_attempt_results) AS attempts, "
                + "(SELECT ROUND(AVG(percentage), 2) FROM v_attempt_results) AS avg_percentage");
        List<Row> recent = db.fetchAll(
                "SELECT r.attempt_id, u.full_name, u.username, r.quiz_title, r.subject_code, r.score, r.max_score, "
                + "r.percentage, r.passed, r.submitted_at FROM v_attempt_results r JOIN users u ON u.id = r.user_id "
                + "ORDER BY r.submitted_at DESC, r.attempt_id DESC LIMIT 10");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totals", totals);
        out.put("recent", recent);
        return out;
    }

    @Override
    public Map<String, Object> analytics(Long userId) {
        List<Row> subjects = db.fetchAll(
                "SELECT s.id, s.code, s.name, COUNT(r.attempt_id) AS attempts, COUNT(DISTINCT r.user_id) AS students, "
                + "ROUND(AVG(r.percentage), 2) AS avg_percentage, "
                + "ROUND(100 * SUM(r.passed) / NULLIF(COUNT(r.attempt_id), 0), 2) AS pass_rate "
                + "FROM subjects s LEFT JOIN v_attempt_results r ON r.subject_id = s.id "
                + "GROUP BY s.id, s.code, s.name ORDER BY s.id");
        List<Row> quizzes = db.fetchAll(
                "SELECT v.quiz_id, v.title, s.code AS subject_code, v.attempts, v.students, v.avg_percentage, "
                + "v.best_percentage, v.pass_rate FROM v_quiz_stats v JOIN subjects s ON s.id = v.subject_id "
                + "ORDER BY v.attempts DESC, v.quiz_id");
        List<Row> distribution = db.fetchAll(
                "SELECT CASE WHEN percentage < 40 THEN '0-39%' WHEN percentage < 60 THEN '40-59%' "
                + "WHEN percentage < 80 THEN '60-79%' ELSE '80-100%' END AS bucket, COUNT(*) AS attempts "
                + "FROM v_attempt_results GROUP BY bucket ORDER BY MIN(percentage)");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subjects", subjects);
        out.put("quizzes", quizzes);
        out.put("distribution", distribution);
        return out;
    }

    public List<Row> students() {
        return db.fetchAll(
                "SELECT u.id, u.username, u.full_name, u.email, u.is_active, u.created_at, "
                + "COUNT(r.attempt_id) AS attempts, ROUND(AVG(r.percentage), 2) AS avg_percentage "
                + "FROM users u LEFT JOIN v_attempt_results r ON r.user_id = u.id WHERE u.role = 'student' "
                + "GROUP BY u.id, u.username, u.full_name, u.email, u.is_active, u.created_at "
                + "ORDER BY u.created_at DESC");
    }

    public List<Row> attempts(int limit) {
        return db.fetchAll(
                "SELECT r.attempt_id, u.full_name, u.username, r.quiz_title, r.subject_code, r.score, r.max_score, "
                + "r.percentage, r.passed, r.submitted_at FROM v_attempt_results r JOIN users u ON u.id = r.user_id "
                + "ORDER BY r.submitted_at DESC LIMIT ?", limit);
    }
}
