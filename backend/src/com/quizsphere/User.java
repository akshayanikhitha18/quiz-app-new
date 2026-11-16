package com.quizsphere;

import com.quizsphere.Errors.ValidationError;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ENCAPSULATION  credentials live in a PRIVATE field with no getter; only verifyPassword() can use them.
 *                Java's `private` is enforced by the compiler: not even Student or Admin can read it.
 * INHERITANCE    Student and Admin extend User and reuse everything common.
 * POLYMORPHISM   getPermissions / getDashboardData / getHomePage are overridden; callers never ask
 *                "which role is this?".
 * ABSTRACTION    User is abstract: it cannot be created directly.
 */
public abstract class User {
    private record Credentials(String hash, String salt, int iterations) {}

    private final long id;
    private String username;
    private String email;
    private String fullName;
    private final LocalDateTime createdAt;
    private final boolean active;
    private final Credentials credentials;          // may be null (e.g. when loaded from a session)

    protected User(long id, String username, String email, String fullName, LocalDateTime createdAt,
                   boolean active, String hash, String salt, Integer iterations) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.createdAt = createdAt;
        this.active = active;
        this.credentials = hash == null ? null : new Credentials(hash, salt, iterations);
    }

    // read-only accessors (no setters: state changes go through methods)
    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isActive() { return active; }

    public abstract String getRole();

    public abstract Set<String> getPermissions();

    public abstract Map<String, Object> getDashboardData(QueryInterface db);

    public abstract String getHomePage();

    public boolean hasPermission(String permission) { return getPermissions().contains(permission); }

    public boolean verifyPassword(String password, PasswordHasher hasher) {
        if (credentials == null) return false;
        return hasher.verify(password, credentials.hash(), credentials.salt(), credentials.iterations());
    }

    /** Validated state change (saving to the database is the repository's job). */
    public void updateProfile(String newFullName, String newEmail) {
        Map<String, String> errors = new LinkedHashMap<>();
        String e1 = Validators.checkFullName(newFullName);
        String e2 = Validators.checkEmail(newEmail);
        if (e1 != null) errors.put("full_name", e1);
        if (e2 != null) errors.put("email", e2);
        if (!errors.isEmpty()) throw new ValidationError("Please correct the highlighted fields.", errors);
        this.fullName = newFullName;
        this.email = newEmail;
    }

    /** Safe to send to the browser: never contains credentials. */
    public Map<String, Object> toPublicDict() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("username", username);
        m.put("email", email);
        m.put("full_name", fullName);
        m.put("role", getRole());
        m.put("created_at", createdAt);
        return m;
    }

    /** Factory: choose the correct subclass from the `role` column. */
    public static User fromRow(Row row) {
        String hash = row.getStr("password_hash");
        String salt = hash == null ? null : row.getStr("password_salt");
        Integer iterations = hash == null ? null : row.getInt("hash_iterations");
        boolean active = !row.containsKey("is_active") || row.isTrue("is_active");
        String role = row.getStr("role");
        if (Student.ROLE.equals(role)) {
            return new Student(row.getLong("id"), row.getStr("username"), row.getStr("email"), row.getStr("full_name"),
                    row.getTime("created_at"), active, hash, salt, iterations);
        }
        if (Admin.ROLE.equals(role)) {
            return new Admin(row.getLong("id"), row.getStr("username"), row.getStr("email"), row.getStr("full_name"),
                    row.getTime("created_at"), active, hash, salt, iterations);
        }
        throw new IllegalStateException("Unknown role: " + role);
    }

    @Override
    public String toString() { return getClass().getSimpleName() + "[id=" + id + ", username=" + username + "]"; }
}
