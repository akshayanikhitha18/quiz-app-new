package com.quizsphere;

import java.util.Map;

/**
 * Application exceptions. Each carries an HTTP status and a short machine-readable code, so the
 * HTTP layer can turn ANY of them into a consistent JSON error response.
 */
public final class Errors {
    private Errors() { }

    public static class AppError extends RuntimeException {
        private final int status;
        private final String code;
        private final Map<String, String> fields;

        public AppError(String message, String code, int status, Map<String, String> fields) {
            super(message);
            this.code = code;
            this.status = status;
            this.fields = fields == null ? Map.of() : fields;
        }

        public int status() { return status; }
        public String code() { return code; }
        public Map<String, String> fields() { return fields; }
    }

    public static class ValidationError extends AppError {
        public ValidationError(String m) { super(m, "validation_error", 422, null); }
        public ValidationError(String m, Map<String, String> fields) { super(m, "validation_error", 422, fields); }
    }

    public static class AuthenticationError extends AppError {
        public AuthenticationError(String m) { super(m, "unauthenticated", 401, null); }
    }

    public static class PermissionDenied extends AppError {
        public PermissionDenied(String m) { super(m, "forbidden", 403, null); }
    }

    public static class NotFound extends AppError {
        public NotFound(String m) { super(m, "not_found", 404, null); }
    }

    public static class Conflict extends AppError {
        public Conflict(String m) { super(m, "conflict", 409, null); }
        public Conflict(String m, String code) { super(m, code, 409, null); }
        public Conflict(String m, Map<String, String> fields) { super(m, "conflict", 409, fields); }
    }

    public static class RateLimited extends AppError {
        public RateLimited(String m) { super(m, "rate_limited", 429, null); }
    }
}
