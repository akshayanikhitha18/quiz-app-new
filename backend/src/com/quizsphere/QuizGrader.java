package com.quizsphere;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * ABSTRACTION: the grading contract. Uses the TEMPLATE METHOD pattern:
 *   grade()        - concrete: loops over answers and builds the total result.
 *   gradeAnswer()  - ABSTRACT: how ONE answer is scored; each concrete grader supplies its own rule.
 * A future TrueFalseGrader or PartialCreditGrader would only implement gradeAnswer().
 * Grading runs ONLY on the server, using the correct options stored in MySQL.
 */
public abstract class QuizGrader {
    public abstract AnswerGrade gradeAnswer(GradingRules rules, long questionId, Long selectedOptionId, long correctOptionId);

    /** answers: rows with question_id, selected_option_id (may be null) and correct_option_id. */
    public GradeResult grade(GradingRules rules, List<Row> answers) {
        List<AnswerGrade> grades = new ArrayList<>();
        for (Row a : answers) {
            Long correct = a.getLong("correct_option_id");
            grades.add(gradeAnswer(rules, a.getLong("question_id"), a.getLong("selected_option_id"), correct == null ? -1L : correct));
        }
        return summarise(rules, grades);
    }

    protected static GradeResult summarise(GradingRules rules, List<AnswerGrade> grades) {
        BigDecimal score = BigDecimal.ZERO;
        int correct = 0, incorrect = 0, unanswered = 0;
        for (AnswerGrade g : grades) {
            score = score.add(g.marks());
            if (g.isCorrect() == null) unanswered++; else if (g.isCorrect()) correct++; else incorrect++;
        }
        score = score.setScale(2, RoundingMode.HALF_UP);
        BigDecimal max = rules.maxScore();
        BigDecimal pct = BigDecimal.ZERO.setScale(2);
        if (max.signum() > 0) {      // a negative score is shown as 0 %
            pct = score.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP).max(BigDecimal.ZERO.setScale(2));
        }
        boolean passed = pct.compareTo(rules.passingPercentage()) >= 0;
        return new GradeResult(score, max, pct, passed, correct, incorrect, unanswered, grades);
    }

    public static String performanceFeedback(BigDecimal percentage, boolean passed) {
        double pct = percentage.doubleValue();
        if (pct >= 90) return "Outstanding work! You have mastered this material.";
        if (pct >= 75) return "Great job! Review the few questions you missed to reach the top.";
        if (passed) return "Good effort - you passed. Revisit the explanations to strengthen weak areas.";
        if (pct >= 25) return "Not there yet. Read the explanations in the review and try again.";
        return "This topic needs more study. Start with the Easy quizzes and build up.";
    }
}
