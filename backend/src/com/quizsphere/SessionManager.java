package com.quizsphere;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Server-side sessions. A random 256-bit token goes into an HttpOnly cookie; only its SHA-256 hash is
 * stored in MySQL, so a leaked database cannot be used to hijack a live session. A NEW token is issued
 * on every login (prevents session fixation). Each session also has a secret CSRF token.
 */
public class SessionManager {
    public record NewSession(String token, String csrfToken) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final QueryInterface db;
    private final Config config;

    public SessionManager(QueryInterface db, Config config) {
        this.db = db;
        this.config = config;
    }

    public static String hashToken(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public int ttlSeconds() { return config.sessionHours() * 3600; }

    public NewSession create(long userId, String ip, String userAgent) {
        LocalDateTime now = DatabaseManager.utcNow();
        db.execute("DELETE FROM sessions WHERE expires_at < ?", now);                 // housekeeping
        byte[] t = new byte[32], c = new byte[32];
        RANDOM.nextBytes(t);
        RANDOM.nextBytes(c);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(t);
        String csrf = HexFormat.of().formatHex(c);
        String ua = userAgent == null ? "" : userAgent;
        db.execute("INSERT INTO sessions (token_hash, csrf_token, user_id, created_at, last_seen_at, expires_at, "
                        + "ip_address, user_agent) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                hashToken(token), csrf, userId, now, now, now.plusSeconds(ttlSeconds()),
                ip.length() > 45 ? ip.substring(0, 45) : ip, ua.length() > 255 ? ua.substring(0, 255) : ua);
        return new NewSession(token, csrf);
    }

    /** Returns the session + user columns, or null if the token is unknown/expired. */
    public Row lookup(String token) {
        if (token == null || token.isEmpty()) return null;
        Row row = db.fetchOne(
                "SELECT s.id AS session_id, s.csrf_token, u.id, u.username, u.email, u.full_name, u.role, "
                + "u.is_active, u.created_at FROM sessions s JOIN users u ON u.id = s.user_id "
                + "WHERE s.token_hash = ? AND s.expires_at > ? AND u.is_active = 1",
                hashToken(token), DatabaseManager.utcNow());
        if (row != null) {
            db.execute("UPDATE sessions SET last_seen_at = ? WHERE id = ?", DatabaseManager.utcNow(), row.getLong("session_id"));
        }
        return row;
    }

    public void destroy(String token) {
        db.execute("DELETE FROM sessions WHERE token_hash = ?", hashToken(token));
    }

    public void destroyOthers(long userId, long keepSessionId) {
        db.execute("DELETE FROM sessions WHERE user_id = ? AND id <> ?", userId, keepSessionId);
    }
}
