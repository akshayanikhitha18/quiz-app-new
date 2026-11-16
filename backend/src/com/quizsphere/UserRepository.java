package com.quizsphere;

/** All SQL about the users table (parameterised). */
public class UserRepository {
    private static final String SAFE = "id, username, email, full_name, role, is_active, created_at";
    private static final String WITH_CREDS = SAFE + ", password_hash, password_salt, hash_iterations";

    public record Taken(boolean username, boolean email) {}

    private final QueryInterface db;

    public UserRepository(QueryInterface db) { this.db = db; }

    public Row findById(long userId) {
        return db.fetchOne("SELECT " + SAFE + " FROM users WHERE id = ?", userId);
    }

    public Row findForLogin(String identifier) {
        return db.fetchOne("SELECT " + WITH_CREDS + " FROM users WHERE username = ? OR email = ? LIMIT 1", identifier, identifier);
    }

    public Row findWithCredentials(long userId) {
        return db.fetchOne("SELECT " + WITH_CREDS + " FROM users WHERE id = ?", userId);
    }

    public Taken taken(String username, String email) {
        boolean u = false, e = false;
        for (Row r : db.fetchAll("SELECT username, email FROM users WHERE username = ? OR email = ?", username, email)) {
            if (r.getStr("username").equalsIgnoreCase(username)) u = true;
            if (r.getStr("email").equalsIgnoreCase(email)) e = true;
        }
        return new Taken(u, e);
    }

    public long insert(String role, java.util.Map<String, String> clean, PasswordHasher.Hashed creds) {
        return db.execute(
                "INSERT INTO users (username, email, full_name, password_hash, password_salt, hash_iterations, role) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                clean.get("username"), clean.get("email"), clean.get("full_name"),
                creds.hash(), creds.salt(), creds.iterations(), role).lastRowId();
    }

    public void updateProfile(long userId, String fullName, String email) {
        db.execute("UPDATE users SET full_name = ?, email = ? WHERE id = ?", fullName, email, userId);
    }

    public void updatePassword(long userId, PasswordHasher.Hashed creds) {
        db.execute("UPDATE users SET password_hash = ?, password_salt = ?, hash_iterations = ? WHERE id = ?",
                creds.hash(), creds.salt(), creds.iterations(), userId);
    }
}
