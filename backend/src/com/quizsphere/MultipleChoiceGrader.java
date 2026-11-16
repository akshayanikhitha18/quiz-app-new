package com.quizsphere;

import java.math.BigDecimal;

/** +marks for a correct option, -negativeMarks for a wrong one, 0 if skipped. */
public class MultipleChoiceGrader extends QuizGrader {
    @Override
    public AnswerGrade gradeAnswer(GradingRules rules, long questionId, Long selectedOptionId, long correctOptionId) {
        if (selectedOptionId == null) {
            return new AnswerGrade(questionId, null, correctOptionId, null, BigDecimal.ZERO);
        }
        if (selectedOptionId == correctOptionId) {
            return new AnswerGrade(questionId, selectedOptionId, correctOptionId, Boolean.TRUE, rules.marksPerQuestion());
        }
        return new AnswerGrade(questionId, selectedOptionId, correctOptionId, Boolean.FALSE, rules.negativeMarks().negate());
    }
}
