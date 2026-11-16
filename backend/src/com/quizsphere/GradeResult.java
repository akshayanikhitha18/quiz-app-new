package com.quizsphere;

import java.math.BigDecimal;
import java.util.List;

public record GradeResult(BigDecimal score, BigDecimal maxScore, BigDecimal percentage, boolean passed,
                          int correct, int incorrect, int unanswered, List<AnswerGrade> answers) {}
