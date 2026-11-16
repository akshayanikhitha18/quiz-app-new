package com.quizsphere;

import com.quizsphere.Errors.Conflict;
import com.quizsphere.Errors.NotFound;
import com.quizsphere.Errors.ValidationError;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Administrator operations (quizzes, questions, students).
 * Data-protection rules:
 *  - Quizzes are never deleted, only activated/deactivated, so history survives.
 *  - A question that appears in any attempt can NOT be deleted, and its text, topic and options are
 *    locked (old results must keep meaning the same thing).
 *  - A question that is still part of a quiz cannot be deleted either.
 */
public class AdminService {
    private static final List<String> DIFFICULTIES = List.of("easy", "medium", "hard");
    private static final String[] LABELS = {"A", "B", "C", "D"};

    private record QuizData(int subjectId, Integer topicId, String title, String description, String difficulty,
                            int durationMinutes, BigDecimal marks, BigDecimal negative, BigDecimal passing,
                            Integer maxAttempts, int allowReview, int isActive) {}

    private record OptionData(String label, String text, int correct) {}

    private record QuestionData(int topicId, String text, String explanation, String difficulty, int isActive,
                                List<OptionData> options) {}

    private final DatabaseManager db;
    private final AdminReport reports;

    public AdminService(DatabaseManager db) {
        this.db = db;
        this.reports = new AdminReport(db);
    }

    // ------------------------------------------------------------- listings
    public List<Row> students() { return reports.students(); }

    public List<Row> attempts() { return reports.attempts(100); }

    public Map<String, Object> analytics() { return reports.analytics(null); }

    public List<Row> topics() {
        return db.fetchAll("SELECT t.id, t.name, t.subject_id, s.code AS subject_code, s.name AS subject_name "
                + "FROM topics t JOIN subjects s ON s.id = t.subject_id ORDER BY t.subject_id, t.id");
    }

    public List<Row> listQuizzes() {
        return db.fetchAll("SELECT x.*, (SELECT COUNT(*) FROM quiz_attempts a WHERE a.quiz_id = x.id) AS attempt_count FROM ("
                + CatalogService.QUIZ_SELECT + ") x ORDER BY x.subject_id, x.id");
    }

    public Row getQuiz(long quizId) {
        Row quiz = null;
        for (Row q : listQuizzes()) if (q.getLong("id") == quizId) { quiz = q; break; }
        if (quiz == null) throw new NotFound("Quiz not found.");
        List<Object> ids = new ArrayList<>();
        for (Row r : db.fetchAll("SELECT question_id FROM quiz_questions WHERE quiz_id = ? ORDER BY position", quizId)) ids.add(r.getLong("question_id"));
        quiz.put("question_ids", ids);
        return quiz;
    }

    // --------------------------------------------------------------- quizzes
    private static QuizData cleanQuiz(Map<String, Object> data) {
        Map<String, String> errors = new LinkedHashMap<>();
        String title = Validators.cleanStr(data.get("title"), 150);
        String description = Validators.cleanStr(data.get("description"), 2000);
        Object difficulty = data.get("difficulty");
        if (title.length() < 3) errors.put("title", "Title must be at least 3 characters.");
        if (description.isEmpty()) errors.put("description", "Description is required.");
        if (!DIFFICULTIES.contains(difficulty)) errors.put("difficulty", "Choose easy, medium or hard.");
        Validators.raiseIf(errors);
        BigDecimal marks = Validators.asDecimal(data.getOrDefault("marks_per_question", 1), "Marks per question", "0.25", "100");
        BigDecimal negative = Validators.asDecimal(data.getOrDefault("negative_marks", 0), "Negative marks", "0", "100").min(marks);
        return new QuizData(
                Validators.asInt(data.get("subject_id"), "Subject", 1),
                Validators.asInt(data.get("topic_id"), "Topic", 1, null, true),
                title, description, (String) difficulty,
                Validators.asInt(data.get("duration_minutes"), "Duration (minutes)", 1, 300, false),
                marks, negative,
                Validators.asDecimal(data.getOrDefault("passing_percentage", 40), "Passing percentage", "0", "100"),
                Validators.asInt(data.get("max_attempts"), "Max attempts", 1, 50, true),
                Validators.asBool(data.getOrDefault("allow_review", true)) ? 1 : 0,
                Validators.asBool(data.getOrDefault("is_active", false)) ? 1 : 0);
    }

    public Row createQuiz(long adminId, Map<String, Object> data) {
        QuizData q = cleanQuiz(data);
        if (q.isActive() == 1) throw new Conflict("Add questions first, then activate the quiz.");
        long id;
        try {
            id = db.execute("INSERT INTO quizzes (subject_id, topic_id, title, description, difficulty, duration_minutes, "
                            + "marks_per_question, negative_marks, passing_percentage, max_attempts, allow_review, is_active, created_by) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    q.subjectId(), q.topicId(), q.title(), q.description(), q.difficulty(), q.durationMinutes(),
                    q.marks(), q.negative(), q.passing(), q.maxAttempts(), q.allowReview(), 0, adminId).lastRowId();
        } catch (DatabaseException.ConstraintViolation e) {
            throw quizError(e);
        }
        return getQuiz(id);
    }

    public Row updateQuiz(long quizId, Map<String, Object> data) {
        Row current = getQuiz(quizId);
        QuizData q = cleanQuiz(data);
        boolean hasQuestions = !((List<?>) current.get("question_ids")).isEmpty();
        if (q.subjectId() != current.getLong("subject_id") && hasQuestions) {
            throw new Conflict("The subject cannot be changed once questions are assigned.");
        }
        if (q.isActive() == 1 && !hasQuestions) throw new Conflict("Add questions before activating this quiz.");
        try {
            db.execute("UPDATE quizzes SET subject_id=?, topic_id=?, title=?, description=?, difficulty=?, duration_minutes=?, "
                            + "marks_per_question=?, negative_marks=?, passing_percentage=?, max_attempts=?, allow_review=?, is_active=? WHERE id=?",
                    q.subjectId(), q.topicId(), q.title(), q.description(), q.difficulty(), q.durationMinutes(),
                    q.marks(), q.negative(), q.passing(), q.maxAttempts(), q.allowReview(), q.isActive(), quizId);
        } catch (DatabaseException.ConstraintViolation e) {
            throw quizError(e);
        }
        return getQuiz(quizId);
    }

    private static RuntimeException quizError(DatabaseException.ConstraintViolation e) {
        if ("duplicate".equals(e.kind)) return new Conflict("A quiz with this title already exists in that subject.", Map.of("title", "Already used."));
        if ("foreign_key".equals(e.kind)) return new ValidationError("The chosen subject/topic combination is not valid.");
        return e;
    }

    public Row setQuizQuestions(long quizId, Map<String, Object> data) {
        Row quiz = getQuiz(quizId);
        if (!(data.get("question_ids") instanceof List<?> raw)) throw new ValidationError("question_ids must be a list.");
        Set<Integer> ids = new LinkedHashSet<>();
        for (Object o : raw) ids.add(Validators.asInt(o, "question id", 1));
        if (quiz.isTrue("is_active") && ids.isEmpty()) {
            throw new Conflict("An active quiz must keep at least one question. Deactivate it first.");
        }
        long subjectId = quiz.getLong("subject_id");
        db.runInTransaction(tx -> {
            for (int qid : ids) {
                if (tx.fetchOne("SELECT 1 AS x FROM questions q JOIN topics t ON t.id = q.topic_id "
                        + "WHERE q.id = ? AND t.subject_id = ? AND q.is_active = 1", qid, subjectId) == null) {
                    throw new ValidationError("Question " + qid + " is not an active question of this quiz's subject.");
                }
            }
            tx.execute("DELETE FROM quiz_questions WHERE quiz_id = ?", quizId);
            int pos = 1;
            for (int qid : ids) tx.execute("INSERT INTO quiz_questions (quiz_id, question_id, position) VALUES (?,?,?)", quizId, qid, pos++);
        });
        return getQuiz(quizId);
    }

    // ------------------------------------------------------------- questions
    public List<Row> listQuestions(Integer subjectId, String difficulty) {
        StringBuilder sql = new StringBuilder("SELECT q.id, q.question_text, q.difficulty, q.is_active, q.topic_id, t.name AS topic_name, "
                + "t.subject_id, s.code AS subject_code, "
                + "(SELECT COUNT(*) FROM attempt_answers aa WHERE aa.question_id = q.id) AS times_used, "
                + "(SELECT COUNT(*) FROM quiz_questions qq WHERE qq.question_id = q.id) AS quiz_count "
                + "FROM questions q JOIN topics t ON t.id = q.topic_id JOIN subjects s ON s.id = t.subject_id WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (subjectId != null && subjectId > 0) {
            sql.append(" AND t.subject_id = ?");
            params.add(subjectId);
        }
        if (difficulty != null && DIFFICULTIES.contains(difficulty)) {
            sql.append(" AND q.difficulty = ?");
            params.add(difficulty);
        }
        sql.append(" ORDER BY t.subject_id, q.id");
        return db.fetchAll(sql.toString(), params);
    }

    public Row getQuestion(long questionId) {
        Row row = db.fetchOne("SELECT q.id, q.topic_id, t.subject_id, q.question_text, q.difficulty, q.explanation, q.is_active, "
                + "(SELECT COUNT(*) FROM attempt_answers aa WHERE aa.question_id = q.id) AS times_used "
                + "FROM questions q JOIN topics t ON t.id = q.topic_id WHERE q.id = ?", questionId);
        if (row == null) throw new NotFound("Question not found.");
        row.put("options", db.fetchAll("SELECT option_label AS label, option_text AS text, is_correct FROM question_options "
                + "WHERE question_id = ? ORDER BY option_label", questionId));
        return row;
    }

    private static QuestionData cleanQuestion(Map<String, Object> data) {
        String text = Validators.cleanStr(data.get("question_text"), 2000);
        String explanation = Validators.cleanStr(data.get("explanation"), 2000);
        Map<String, String> errors = new LinkedHashMap<>();
        if (text.length() < 10) errors.put("question_text", "Question must be at least 10 characters.");
        if (explanation.isEmpty()) errors.put("explanation", "An explanation is required.");
        if (!DIFFICULTIES.contains(data.get("difficulty"))) errors.put("difficulty", "Choose easy, medium or hard.");
        List<OptionData> options = new ArrayList<>();
        if (!(data.get("options") instanceof List<?> raw) || raw.size() != 4) {
            errors.put("options", "Exactly four options are required.");
        } else {
            List<String> texts = new ArrayList<>();
            List<Integer> correct = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Object o = raw.get(i);
                Map<?, ?> m = o instanceof Map<?, ?> mm ? mm : Map.of();
                texts.add(Validators.cleanStr(m.get("text"), 500));
                if (Validators.asBool(m.get("is_correct"))) correct.add(i);
            }
            if (texts.stream().anyMatch(String::isEmpty)) errors.put("options", "All four options need text.");
            else if (texts.stream().map(String::toLowerCase).distinct().count() != 4) errors.put("options", "Options must be different from each other.");
            else if (correct.size() != 1) errors.put("options", "Mark exactly one option as correct.");
            else for (int i = 0; i < 4; i++) options.add(new OptionData(LABELS[i], texts.get(i), i == correct.get(0) ? 1 : 0));
        }
        Validators.raiseIf(errors);
        return new QuestionData(Validators.asInt(data.get("topic_id"), "Topic", 1), text, explanation,
                (String) data.get("difficulty"), Validators.asBool(data.getOrDefault("is_active", true)) ? 1 : 0, options);
    }

    public Row createQuestion(long adminId, Map<String, Object> data) {
        QuestionData q = cleanQuestion(data);
        long id;
        try {
            id = db.inTransaction(tx -> {
                long qid = tx.execute("INSERT INTO questions (topic_id, question_text, difficulty, explanation, is_active, created_by) "
                        + "VALUES (?,?,?,?,?,?)", q.topicId(), q.text(), q.difficulty(), q.explanation(), q.isActive(), adminId).lastRowId();
                for (OptionData o : q.options()) {
                    tx.execute("INSERT INTO question_options (question_id, option_label, option_text, is_correct) VALUES (?,?,?,?)",
                            qid, o.label(), o.text(), o.correct());
                }
                return qid;
            });
        } catch (DatabaseException.ConstraintViolation e) {
            if ("foreign_key".equals(e.kind)) throw new ValidationError("The chosen topic does not exist.");
            throw e;
        }
        return getQuestion(id);
    }

    public Row updateQuestion(long questionId, Map<String, Object> data) {
        Row current = getQuestion(questionId);
        QuestionData q = cleanQuestion(data);
        boolean used = current.getLong("times_used") > 0;
        if (used) {
            boolean same = q.topicId() == current.getInt("topic_id") && q.text().equals(current.getStr("question_text"));
            @SuppressWarnings("unchecked") List<Row> old = (List<Row>) current.get("options");
            for (int i = 0; same && i < 4; i++) {
                same = old.get(i).getStr("label").equals(q.options().get(i).label())
                        && old.get(i).getStr("text").equals(q.options().get(i).text())
                        && (old.get(i).isTrue("is_correct") ? 1 : 0) == q.options().get(i).correct();
            }
            if (!same) {
                throw new Conflict("This question is part of past attempts, so its text, topic and options are locked. "
                        + "You may change the explanation, difficulty and active flag, or create a new question.");
            }
        }
        try {
            db.runInTransaction(tx -> {
                tx.execute("UPDATE questions SET topic_id=?, question_text=?, difficulty=?, explanation=?, is_active=? WHERE id=?",
                        q.topicId(), q.text(), q.difficulty(), q.explanation(), q.isActive(), questionId);
                if (!used) {
                    tx.execute("UPDATE question_options SET is_correct = 0 WHERE question_id = ?", questionId);   // avoid 2 correct at once
                    for (OptionData o : q.options()) {
                        tx.execute("UPDATE question_options SET option_text = ?, is_correct = ? WHERE question_id = ? AND option_label = ?",
                                o.text(), o.correct(), questionId, o.label());
                    }
                }
            });
        } catch (DatabaseException.ConstraintViolation e) {
            if ("foreign_key".equals(e.kind)) throw new ValidationError("The chosen topic does not exist.");
            throw e;
        }
        return getQuestion(questionId);
    }

    public Map<String, Object> deleteQuestion(long questionId) {
        Row info = getQuestion(questionId);
        if (info.getLong("times_used") > 0) {
            throw new Conflict("This question was used in student attempts and cannot be deleted. Deactivate it instead.", "in_use");
        }
        if (db.fetchOne("SELECT 1 AS x FROM quiz_questions WHERE question_id = ?", questionId) != null) {
            throw new Conflict("Remove this question from all quizzes before deleting it.", "in_quiz");
        }
        db.execute("DELETE FROM questions WHERE id = ?", questionId);          // options are removed by ON DELETE CASCADE
        return Map.of("deleted", true);
    }
}
