package com.quizsphere;

import com.quizsphere.Errors.Conflict;
import com.quizsphere.Errors.NotFound;

import java.math.BigDecimal;

/** Quiz configuration plus the rules about starting it. */
public class Quiz {
    private final long id;
    private final long subjectId;
    private final String title;
    private final int durationMinutes;
    private final BigDecimal marksPerQuestion;
    private final BigDecimal negativeMarks;
    private final BigDecimal passingPercentage;
    private final Integer maxAttempts;       // null = unlimited
    private final boolean allowReview;
    private final boolean active;

    public Quiz(Row r) {
        this.id = r.getLong("id");
        this.subjectId = r.getLong("subject_id");
        this.title = r.getStr("title");
        this.durationMinutes = r.getInt("duration_minutes");
        this.marksPerQuestion = r.getDec("marks_per_question");
        this.negativeMarks = r.getDec("negative_marks");
        this.passingPercentage = r.getDec("passing_percentage");
        this.maxAttempts = r.getInt("max_attempts");
        this.allowReview = r.isTrue("allow_review");
        this.active = r.isTrue("is_active");
    }

    public long getId() { return id; }
    public long getSubjectId() { return subjectId; }
    public String getTitle() { return title; }
    public BigDecimal getMarksPerQuestion() { return marksPerQuestion; }
    public BigDecimal getNegativeMarks() { return negativeMarks; }
    public BigDecimal getPassingPercentage() { return passingPercentage; }
    public boolean isAllowReview() { return allowReview; }
    public long timeLimitSeconds() { return durationMinutes * 60L; }
    public boolean negativeMarkingEnabled() { return negativeMarks.signum() > 0; }

    public void ensureCanStart(long attemptsUsed) {
        if (!active) throw new NotFound("Quiz not found.");
        if (maxAttempts != null && attemptsUsed >= maxAttempts) {
            throw new Conflict("You have used all " + maxAttempts + " attempts allowed for this quiz.", "attempts_exhausted");
        }
    }
}
