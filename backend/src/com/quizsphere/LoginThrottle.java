package com.quizsphere;

import java.time.LocalDateTime;

/** Brute-force protection: too many recent failures => temporarily blocked. */
public class LoginThrottle {
    private final QueryInterface db;
    private final Config config;

    public LoginThrottle(QueryInterface db, Config config) {
        this.db = db;
        this.config = config;
    }

    private LocalDateTime since() { return DatabaseManager.utcNow().minusMinutes(config.loginWindowMinutes()); }

    public boolean isBlocked(String identifier, String ip) {
        long byName = db.fetchOne("SELECT COUNT(*) AS c FROM login_attempts WHERE identifier = ? AND success = 0 AND attempted_at > ?",
                identifier, since()).getLong("c");
        long byIp = db.fetchOne("SELECT COUNT(*) AS c FROM login_attempts WHERE ip_address = ? AND success = 0 AND attempted_at > ?",
                ip, since()).getLong("c");
        return byName >= config.loginMaxAttempts() || byIp >= config.loginMaxAttempts() * 5L;
    }

    public void record(String identifier, String ip, boolean success) {
        db.execute("INSERT INTO login_attempts (identifier, ip_address, success, attempted_at) VALUES (?, ?, ?, ?)",
                identifier, ip, success ? 1 : 0, DatabaseManager.utcNow());
        if (success) db.execute("DELETE FROM login_attempts WHERE identifier = ? AND success = 0", identifier);
    }
}
