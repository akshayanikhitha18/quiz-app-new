package com.quizsphere;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

/** One database row: column label -> value, with typed getters (Java's version of a Python dict row). */
public class Row extends LinkedHashMap<String, Object> {
    public Integer getInt(String key) {
        Object v = get(key);
        return v == null ? null : ((Number) v).intValue();
    }

    public Long getLong(String key) {
        Object v = get(key);
        return v == null ? null : ((Number) v).longValue();
    }

    public String getStr(String key) {
        Object v = get(key);
        return v == null ? null : v.toString();
    }

    public BigDecimal getDec(String key) {
        Object v = get(key);
        if (v == null) return null;
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    public boolean isTrue(String key) {
        Object v = get(key);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return ((Number) v).intValue() != 0;
    }

    public LocalDateTime getTime(String key) {
        Object v = get(key);
        if (v == null) return null;
        if (v instanceof LocalDateTime t) return t;
        if (v instanceof Timestamp t) return t.toLocalDateTime();
        throw new IllegalStateException("Column " + key + " is not a date-time");
    }
}
