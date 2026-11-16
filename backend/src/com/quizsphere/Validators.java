package com.quizsphere;

import com.quizsphere.Errors.ValidationError;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server-side input validation. The browser validates too, but a browser can be bypassed
 * (curl, DevTools), so THIS is the check that actually counts.
 */
public final class Validators {
    private Validators() { }

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final Pattern LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern INTEGER = Pattern.compile("^-?\\d+$");

    public static String cleanStr(Object value, int maxLen) {
        if (!(value instanceof String s)) return "";
        s = s.strip();
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    private static String plainString(Object v) { return v instanceof String s ? s : ""; }

    public static String checkFullName(String v) {
        return v.length() < 2 || v.length() > 100 ? "Full name must be 2-100 characters." : null;
    }

    public static String checkUsername(String v) {
        return USERNAME.matcher(v).matches() ? null : "Username must be 3-30 letters, digits or underscores.";
    }

    public static String checkEmail(String v) {
        return v.length() > 120 || !EMAIL.matcher(v).matches() ? "Enter a valid email address." : null;
    }

    public static String checkPassword(String v) {
        if (v.length() < 8 || v.length() > 128) return "Password must be 8-128 characters.";
        if (!(LETTER.matcher(v).find() && DIGIT.matcher(v).find())) return "Password must contain at least one letter and one digit.";
        return null;
    }

    public static void raiseIf(Map<String, String> errors) {
        if (!errors.isEmpty()) throw new ValidationError("Please correct the highlighted fields.", errors);
    }

    private static void put(Map<String, String> errors, String key, String error) {
        if (error != null) errors.put(key, error);
    }

    /** Returns the cleaned values: full_name, username, email, password. */
    public static Map<String, String> validateRegistration(Map<String, Object> data) {
        String fullName = cleanStr(data.get("full_name"), 200);
        String username = cleanStr(data.get("username"), 100);
        String email = cleanStr(data.get("email"), 200).toLowerCase();
        String password = plainString(data.get("password"));
        String confirm = plainString(data.get("confirm_password"));
        Map<String, String> errors = new LinkedHashMap<>();
        put(errors, "full_name", checkFullName(fullName));
        put(errors, "username", checkUsername(username));
        put(errors, "email", checkEmail(email));
        put(errors, "password", checkPassword(password));
        if (!errors.containsKey("password") && !password.equals(confirm)) errors.put("confirm_password", "Passwords do not match.");
        raiseIf(errors);
        Map<String, String> clean = new LinkedHashMap<>();
        clean.put("full_name", fullName);
        clean.put("username", username);
        clean.put("email", email);
        clean.put("password", password);
        return clean;
    }

    public static Map<String, String> validateProfile(Map<String, Object> data) {
        String fullName = cleanStr(data.get("full_name"), 200);
        String email = cleanStr(data.get("email"), 200).toLowerCase();
        Map<String, String> errors = new LinkedHashMap<>();
        put(errors, "full_name", checkFullName(fullName));
        put(errors, "email", checkEmail(email));
        raiseIf(errors);
        return Map.of("full_name", fullName, "email", email);
    }

    /** Returns {currentPassword, newPassword}. */
    public static String[] validatePasswordChange(Map<String, Object> data) {
        String current = plainString(data.get("current_password"));
        String next = plainString(data.get("new_password"));
        String confirm = plainString(data.get("confirm_password"));
        Map<String, String> errors = new LinkedHashMap<>();
        if (current.isEmpty()) errors.put("current_password", "Enter your current password.");
        String pwError = checkPassword(next);
        if (pwError != null) errors.put("new_password", pwError);
        else if (!next.equals(confirm)) errors.put("confirm_password", "Passwords do not match.");
        raiseIf(errors);
        return new String[]{current, next};
    }

    /** Whole number from JSON (number or numeric string). Returns null only when nullable and empty. */
    public static Integer asInt(Object value, String label, Integer min, Integer max, boolean nullable) {
        if (value == null || "".equals(value)) {
            if (nullable) return null;
            throw new ValidationError(label + " is required.", Map.of(label, "Required."));
        }
        long n;
        if (value instanceof Boolean) {
            throw new ValidationError(label + " must be a whole number.");
        } else if (value instanceof Number num && num.doubleValue() == Math.rint(num.doubleValue())) {
            n = num.longValue();
        } else if (value instanceof String s && INTEGER.matcher(s.strip()).matches() && s.strip().length() < 10) {
            n = Long.parseLong(s.strip());
        } else {
            throw new ValidationError(label + " must be a whole number.");
        }
        if ((min != null && n < min) || (max != null && n > max)) {
            throw new ValidationError(label + " must be between " + min + " and " + max + ".");
        }
        return (int) n;
    }

    public static int asInt(Object value, String label, int min) {
        return asInt(value, label, min, null, false);
    }

    public static BigDecimal asDecimal(Object value, String label, String min, String max) {
        BigDecimal n;
        try {
            n = new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ValidationError(label + " must be a number.");
        }
        if (n.compareTo(new BigDecimal(min)) < 0 || n.compareTo(new BigDecimal(max)) > 0) {
            throw new ValidationError(label + " must be between " + min + " and " + max + ".");
        }
        return n.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean asBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() == 1;
        return value != null && java.util.List.of("true", "1", "yes", "on").contains(value.toString().toLowerCase());
    }
}
