package com.quizsphere;

import java.math.BigDecimal;

/** isCorrect == null means "unanswered". */
public record AnswerGrade(long questionId, Long selectedOptionId, long correctOptionId, Boolean isCorrect, BigDecimal marks) {}
