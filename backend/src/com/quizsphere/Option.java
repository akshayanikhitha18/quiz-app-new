package com.quizsphere;

import java.util.LinkedHashMap;
import java.util.Map;

public class Option {
    private final long id;
    private final String label;
    private final String text;
    private final boolean correct;

    public Option(long id, String label, String text, boolean correct) {
        this.id = id;
        this.label = label;
        this.text = text;
        this.correct = correct;
    }

    public long getId() { return id; }
    public boolean isCorrect() { return correct; }

    /** What the browser receives DURING a quiz: no answer information at all. */
    public Map<String, Object> toPublicDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("text", text);
        return m;
    }

    public Map<String, Object> toReviewDict() {
        Map<String, Object> m = toPublicDict();
        m.put("is_correct", correct);
        return m;
    }
}
