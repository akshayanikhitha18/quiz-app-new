package com.quizsphere;

import java.util.LinkedHashMap;
import java.util.Map;

public record Subject(long id, String code, String name, String description, String accentColor) {
    public static Subject fromRow(Row r) {
        return new Subject(r.getLong("id"), r.getStr("code"), r.getStr("name"), r.getStr("description"), r.getStr("accent_color"));
    }

    public Map<String, Object> toDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("code", code);
        m.put("name", name);
        m.put("description", description);
        m.put("accent_color", accentColor);
        return m;
    }
}
