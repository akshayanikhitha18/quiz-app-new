package com.quizsphere;

import com.quizsphere.Errors.Conflict;
import com.quizsphere.Errors.NotFound;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One attempt. All state is private; submit() is the ONLY way to finish it, and it refuses to run
 * twice (duplicate-submission protection inside the object itself).
 */
public class QuizAttempt {
    public static final String IN_PROGRESS = "in_progress";
    public static final String SUBMITTED = "submitted";

    /** Values the service writes to the database after a successful submit(). */
    public record SubmitValues(LocalDateTime submittedAt, int autoSubmitted, BigDecimal score, int passed) {}

    private final long id;
    private final long userId;
    private final long quizId;
    private String status;
    private final LocalDateTime deadlineAt;
    private final GradingRules rules;
    private LocalDateTime submittedAt;
    private BigDecimal score;
    private boolean autoSubmitted;

    public QuizAttempt(long id, long userId, long quizId, String status, LocalDateTime deadlineAt, GradingRules rules) {
        this.id = id;
        this.userId = userId;
        this.quizId = quizId;
        this.status = status;
        this.deadlineAt = deadlineAt;
        this.rules = rules;
    }

    public static QuizAttempt fromRow(Row r) {
        GradingRules rules = new GradingRules(r.getDec("marks_per_question"), r.getDec("negative_marks"),
                r.getDec("passing_percentage"), r.getInt("total_questions"));
        return new QuizAttempt(r.getLong("id"), r.getLong("user_id"), r.getLong("quiz_id"), r.getStr("status"),
                r.getTime("deadline_at"), rules);
    }

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public long getQuizId() { return quizId; }
    public GradingRules getRules() { return rules; }
    public BigDecimal getScore() { return score; }              // read-only: set only by submit()
    public boolean isSubmitted() { return SUBMITTED.equals(status); }

    /** Student data isolation. Same message as "missing", so ids cannot be probed. */
    public void ensureOwnedBy(long otherUserId) {
        if (this.userId != otherUserId) throw new NotFound("Attempt not found.");
    }

    public int secondsRemaining(LocalDateTime now) {
        return (int) Math.max(0, Duration.between(now, deadlineAt).getSeconds());
    }

    public boolean isExpired(LocalDateTime now) { return now.isAfter(deadlineAt); }

    public boolean canAcceptAnswers(LocalDateTime now, int graceSeconds) {
        return !isSubmitted() && !now.isAfter(deadlineAt.plusSeconds(graceSeconds));
    }

    /** Finish the attempt with a GradeResult computed by the server-side grader. */
    public SubmitValues submit(GradeResult result, LocalDateTime now, boolean forcedAuto) {
        if (isSubmitted()) throw new Conflict("This attempt has already been submitted.", "already_submitted");
        boolean late = now.isAfter(deadlineAt);
        this.status = SUBMITTED;
        this.submittedAt = late ? deadlineAt : now;             // never later than the deadline
        this.autoSubmitted = forcedAuto || late;
        this.score = result.score();
        return new SubmitValues(submittedAt, autoSubmitted ? 1 : 0, score, result.passed() ? 1 : 0);
    }
}
