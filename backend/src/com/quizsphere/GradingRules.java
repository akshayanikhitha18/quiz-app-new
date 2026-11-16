package com.quizsphere;

import java.math.BigDecimal;

/** Snapshot of the rules an attempt was started with (immutable). */
public record GradingRules(BigDecimal marksPerQuestion, BigDecimal negativeMarks,
                           BigDecimal passingPercentage, int totalQuestions) {
    public BigDecimal maxScore() {
        return marksPerQuestion.multiply(BigDecimal.valueOf(totalQuestions));
    }
}
