package com.quizsphere;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader/writer (Java has no JSON support in its standard library).
 * parse():     object -> LinkedHashMap, array -> ArrayList, number -> Long or Double, plus String/Boolean/null.
 * stringify(): Map, Iterable, String, Number, Boolean, LocalDateTime (as UTC ISO text) and null.
 */
public final class Json {
    private Json() { }

    public static class JsonException extends RuntimeException {
        public JsonException(String message) { super(message); }
    }

    // ------------------------------------------------------------------ parsing
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value(0);
        p.skipWs();
        if (!p.done()) throw new JsonException("Unexpected data after JSON value");
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) { this.s = s; }

        boolean done() { return i >= s.length(); }

        void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        Object value(int depth) {
            if (depth > 32) throw new JsonException("JSON is nested too deeply");
            if (done()) throw new JsonException("Unexpected end of JSON");
            char c = s.charAt(i);
            if (c == '{') return object(depth);
            if (c == '[') return array(depth);
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            if (c == '-' || (c >= '0' && c <= '9')) return number();
            throw new JsonException("Unexpected character '" + c + "'");
        }

        Map<String, Object> object(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            skipWs();
            if (!done() && s.charAt(i) == '}') { i++; return m; }
            while (true) {
                skipWs();
                if (done() || s.charAt(i) != '"') throw new JsonException("Expected a string key");
                String key = string();
                skipWs();
                if (done() || s.charAt(i++) != ':') throw new JsonException("Expected ':'");
                skipWs();
                m.put(key, value(depth + 1));
                skipWs();
                if (done()) throw new JsonException("Unterminated object");
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw new JsonException("Expected ',' or '}'");
            }
        }

        List<Object> array(int depth) {
            List<Object> list = new ArrayList<>();
            i++;
            skipWs();
            if (!done() && s.charAt(i) == ']') { i++; return list; }
            while (true) {
                skipWs();
                list.add(value(depth + 1));
                skipWs();
                if (done()) throw new JsonException("Unterminated array");
                char c = s.charAt(i++);
                if (c == ']') return list;
                if (c != ',') throw new JsonException("Expected ',' or ']'");
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (true) {
                if (done()) throw new JsonException("Unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c < 0x20) throw new JsonException("Control character in string");
                if (c != '\\') { sb.append(c); continue; }
                if (done()) throw new JsonException("Bad escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new JsonException("Bad unicode escape");
                        try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                        catch (NumberFormatException ex) { throw new JsonException("Bad unicode escape"); }
                        i += 4;
                        break;
                    default: throw new JsonException("Bad escape");
                }
            }
        }

        Object number() {
            int start = i;
            boolean floating = false;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                if (".eE".indexOf(s.charAt(i)) >= 0) floating = true;
                i++;
            }
            String t = s.substring(start, i);
            try {
                if (!floating) {
                    try { return Long.parseLong(t); } catch (NumberFormatException big) { return Double.parseDouble(t); }
                }
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                throw new JsonException("Bad number");
            }
        }
    }

    // ------------------------------------------------------------------ writing
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof CharSequence cs) {
            quote(sb, cs.toString());
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof BigDecimal bd) {
            sb.append(bd.toPlainString());
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            sb.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : BigDecimal.valueOf(d).toPlainString());
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof LocalDateTime t) {
            quote(sb, t.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z");
        } else if (v instanceof Timestamp t) {
            write(sb, t.toLocalDateTime());
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(sb, o);
            }
            sb.append(']');
        } else {
            quote(sb, v.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20 || c == '<' || c == '>') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }
}
