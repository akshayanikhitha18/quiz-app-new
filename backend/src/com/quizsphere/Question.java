package com.quizsphere;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Question {
    private final long id;
    private final int position;
    private final String text;
    private final List<Option> options;
    private final Long selectedOptionId;
    private final boolean marked;
    private final String explanation;

    public Question(long id, int position, String text, List<Option> options, Long selectedOptionId,
                    boolean marked, String explanation) {
        this.id = id;
        this.position = position;
        this.text = text;
        this.options = options;
        this.selectedOptionId = selectedOptionId;
        this.marked = marked;
        this.explanation = explanation;
    }

    private Map<String, Object> base() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("question_id", id);
        m.put("position", position);
        m.put("text", text);
        return m;
    }

    /** During the quiz: question text and options only. */
    public Map<String, Object> toPublicDict() {
        Map<String, Object> m = base();
        List<Object> opts = new ArrayList<>();
        for (Option o : options) opts.add(o.toPublicDict());
        m.put("options", opts);
        m.put("selected_option_id", selectedOptionId);
        m.put("marked_for_review", marked);
        return m;
    }

    /** After submission only. */
    public Map<String, Object> toReviewDict() {
        Long correct = null;
        List<Object> opts = new ArrayList<>();
        for (Option o : options) {
            opts.add(o.toReviewDict());
            if (o.isCorrect()) correct = o.getId();
        }
        String status = selectedOptionId == null ? "unanswered" : (selectedOptionId.equals(correct) ? "correct" : "incorrect");
        Map<String, Object> m = base();
        m.put("options", opts);
        m.put("selected_option_id", selectedOptionId);
        m.put("correct_option_id", correct);
        m.put("status", status);
        m.put("explanation", explanation);
        return m;
    }
}
