package com.quizsphere;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public class Student extends User {
    public static final String ROLE = "student";
    private static final Set<String> PERMISSIONS = Set.of("quiz:take", "results:own", "profile:own");

    public Student(long id, String username, String email, String fullName, LocalDateTime createdAt,
                   boolean active, String hash, String salt, Integer iterations) {
        super(id, username, email, fullName, createdAt, active, hash, salt, iterations);
    }

    @Override public String getRole() { return ROLE; }

    @Override public Set<String> getPermissions() { return PERMISSIONS; }

    @Override public String getHomePage() { return "dashboard.html"; }

    @Override
    public Map<String, Object> getDashboardData(QueryInterface db) {
        return new StudentReport(db).dashboard(getId());
    }
}
