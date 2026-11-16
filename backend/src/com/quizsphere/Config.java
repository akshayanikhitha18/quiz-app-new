package com.quizsphere;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application settings, read from ENVIRONMENT VARIABLES or a local ".env" file that is never
 * committed to Git. Nothing sensitive is written in source code.
 * A record is immutable: fields cannot change after creation (a simple form of encapsulation).
 */
public record Config(String dbHost, int dbPort, String dbUser, String dbPassword, String dbName, int dbPoolSize,
                     String serverHost, int serverPort, int sessionHours, int pbkdf2Iterations,
                     int loginMaxAttempts, int loginWindowMinutes, boolean cookieSecure, int deadlineGraceSeconds) {

    public static class ConfigException extends RuntimeException {
        public ConfigException(String message) { super(message); }
    }

    /** Never print the password: the generated toString() is replaced. */
    @Override
    public String toString() {
        return "Config[" + dbUser + "@" + dbHost + ":" + dbPort + "/" + dbName + ", server " + serverHost + ":" + serverPort + "]";
    }

    /** Run the program from the project root so ".env" is found there. */
    public static Config load() {
        Map<String, String> v = new HashMap<>(readEnvFile(Path.of("").toAbsolutePath().resolve(".env")));
        v.putAll(System.getenv());                        // real environment variables win

        String password = v.getOrDefault("DB_PASSWORD", "");
        if (password.isEmpty()) {
            throw new ConfigException("DB_PASSWORD is not set. Copy .env.example to .env and put your "
                    + "MySQL user's password in it (see README).");
        }
        return new Config(
                v.getOrDefault("DB_HOST", "127.0.0.1").trim(),
                readInt(v, "DB_PORT", 3306, 1, 65535),
                v.getOrDefault("DB_USER", "quizsphere_user").trim(),
                password,
                v.getOrDefault("DB_NAME", "quizsphere").trim(),
                readInt(v, "DB_POOL_SIZE", 8, 1, 32),
                v.getOrDefault("SERVER_HOST", "127.0.0.1").trim(),
                readInt(v, "SERVER_PORT", 8000, 1, 65535),
                readInt(v, "SESSION_HOURS", 8, 1, 168),
                readInt(v, "PBKDF2_ITERATIONS", 600_000, 100_000, 5_000_000),
                readInt(v, "LOGIN_MAX_ATTEMPTS", 5, 1, 100),
                readInt(v, "LOGIN_WINDOW_MINUTES", 15, 1, 1440),
                readBool(v, "COOKIE_SECURE", false),
                readInt(v, "DEADLINE_GRACE_SECONDS", 5, 0, 60));
    }

    /** Java has no built-in .env reader, so a tiny one is written here. */
    private static Map<String, String> readEnvFile(Path path) {
        Map<String, String> out = new HashMap<>();
        if (!Files.isRegularFile(path)) return out;
        try {
            for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = raw.strip();
                if (line.startsWith("\uFEFF")) line = line.substring(1).strip();     // BOM from Windows Notepad
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                int eq = line.indexOf('=');
                String key = line.substring(0, eq).strip();
                String value = line.substring(eq + 1).strip();
                if (value.length() >= 2 && value.charAt(0) == value.charAt(value.length() - 1)
                        && (value.charAt(0) == '"' || value.charAt(0) == '\'')) {
                    value = value.substring(1, value.length() - 1);
                }
                out.put(key, value);
            }
        } catch (IOException e) {
            throw new ConfigException("Could not read the .env file: " + e.getMessage());
        }
        return out;
    }

    private static int readInt(Map<String, String> v, String name, int def, int min, int max) {
        String raw = v.getOrDefault(name, String.valueOf(def)).trim();
        int n;
        try {
            n = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ConfigException(name + " must be a whole number, got '" + raw + "'.");
        }
        if (n < min || n > max) throw new ConfigException(name + " must be between " + min + " and " + max + ".");
        return n;
    }

    private static boolean readBool(Map<String, String> v, String name, boolean def) {
        String raw = v.get(name);
        if (raw == null) return def;
        return List.of("1", "true", "yes", "on").contains(raw.trim().toLowerCase());
    }
}
