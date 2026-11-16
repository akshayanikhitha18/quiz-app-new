package com.quizsphere;

import com.quizsphere.Errors.AuthenticationError;
import com.quizsphere.Errors.Conflict;
import com.quizsphere.Errors.RateLimited;
import com.quizsphere.Errors.ValidationError;

import java.util.LinkedHashMap;
import java.util.Map;

/** Facade that COMPOSES the helpers (hasher, repository, sessions, throttle): composition, not inheritance. */
public class AuthenticationService {
    public record LoginResult(User user, String token, String csrfToken) {}

    public record Authenticated(User user, long sessionId, String csrfToken) {}

    private final PasswordHasher hasher;
    private final UserRepository users;
    private final SessionManager sessions;
    private final LoginThrottle throttle;

    public AuthenticationService(QueryInterface db, Config config) {
        this.hasher = new PasswordHasher(config.pbkdf2Iterations());
        this.users = new UserRepository(db);
        this.sessions = new SessionManager(db, config);
        this.throttle = new LoginThrottle(db, config);
    }

    public PasswordHasher hasher() { return hasher; }
    public SessionManager sessions() { return sessions; }

    // ---- accounts ----
    public User register(Map<String, Object> data) {
        return createUser(Student.ROLE, Validators.validateRegistration(data));
    }

    public User createAdmin(String username, String email, String fullName, String password, String confirm) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("username", username);
        raw.put("email", email);
        raw.put("full_name", fullName);
        raw.put("password", password);
        raw.put("confirm_password", confirm);
        return createUser(Admin.ROLE, Validators.validateRegistration(raw));
    }

    private User createUser(String role, Map<String, String> clean) {
        UserRepository.Taken taken = users.taken(clean.get("username"), clean.get("email"));
        if (taken.username() || taken.email()) {
            Map<String, String> fields = new LinkedHashMap<>();
            if (taken.username()) fields.put("username", "That username is already taken.");
            if (taken.email()) fields.put("email", "That email is already registered.");
            throw new Conflict("Account could not be created.", fields);
        }
        PasswordHasher.Hashed creds = hasher.hashPassword(clean.get("password"));
        long id;
        try {
            id = users.insert(role, clean, creds);
        } catch (DatabaseException.ConstraintViolation e) {
            if ("duplicate".equals(e.kind)) throw new Conflict("Username or email is already registered.");
            throw e;
        }
        return User.fromRow(users.findById(id));
    }

    // ---- login / logout ----
    public LoginResult login(String identifier, String password, String ip, String userAgent) {
        identifier = identifier == null ? "" : identifier.strip();
        if (identifier.isEmpty() || password == null || password.isEmpty()) {
            throw new ValidationError("Enter your username or email and your password.");
        }
        String key = identifier.toLowerCase();
        if (throttle.isBlocked(key, ip)) {
            throw new RateLimited("Too many failed attempts. Please wait a few minutes and try again.");
        }
        Row row = users.findForLogin(identifier);
        User user = row == null ? null : User.fromRow(row);
        boolean ok = false;
        if (user != null) ok = user.verifyPassword(password, hasher) && user.isActive();
        else hasher.burnTime(password);
        throttle.record(key, ip, ok);
        if (!ok) throw new AuthenticationError("Invalid username/email or password.");
        SessionManager.NewSession s = sessions.create(user.getId(), ip, userAgent);
        return new LoginResult(user, s.token(), s.csrfToken());
    }

    public void logout(String token) {
        if (token != null && !token.isEmpty()) sessions.destroy(token);
    }

    public Authenticated authenticate(String token) {
        Row row = sessions.lookup(token);
        if (row == null) throw new AuthenticationError("Your session has expired. Please log in again.");
        return new Authenticated(User.fromRow(row), row.getLong("session_id"), row.getStr("csrf_token"));
    }

    // ---- profile ----
    public User updateProfile(User user, Map<String, Object> data) {
        Map<String, String> clean = Validators.validateProfile(data);
        user.updateProfile(clean.get("full_name"), clean.get("email"));             // validated state change
        try {
            users.updateProfile(user.getId(), user.getFullName(), user.getEmail());
        } catch (DatabaseException.ConstraintViolation e) {
            if ("duplicate".equals(e.kind)) {
                throw new Conflict("That email is already registered.", Map.of("email", "Already in use."));
            }
            throw e;
        }
        return User.fromRow(users.findById(user.getId()));
    }

    public void changePassword(User user, Map<String, Object> data, long sessionId) {
        String[] pw = Validators.validatePasswordChange(data);
        User stored = User.fromRow(users.findWithCredentials(user.getId()));
        if (!stored.verifyPassword(pw[0], hasher)) {
            throw new ValidationError("Current password is incorrect.", Map.of("current_password", "Current password is incorrect."));
        }
        users.updatePassword(user.getId(), hasher.hashPassword(pw[1]));
        sessions.destroyOthers(user.getId(), sessionId);                            // log out other devices
    }
}
