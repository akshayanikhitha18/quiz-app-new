package com.quizsphere;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Route table + one small handler per endpoint. Handlers only translate HTTP <-> service calls. */
final class ApiController {
    private static final String STUDENT = "quiz:take";
    private static final String ADMIN = "admin:manage";
    private static final String AUTH = Authorizer.AUTHENTICATED;

    private final Config config;
    private final DatabaseManager db;
    private final AuthenticationService auth;
    private final CatalogService catalog;
    private final QuizService quizzes;
    private final AdminService admin;

    ApiController(Config config, DatabaseManager db, AuthenticationService auth, CatalogService catalog,
                  QuizService quizzes, AdminService admin) {
        this.config = config;
        this.db = db;
        this.auth = auth;
        this.catalog = catalog;
        this.quizzes = quizzes;
        this.admin = admin;
    }

    void register(Application.Router r) {
        r.add("POST", "/api/register", null, this::register);
        r.add("POST", "/api/login", null, this::login);
        r.add("GET", "/api/public/subjects", null, q -> ApiResponse.ok(catalog.publicSubjects()));
        r.add("POST", "/api/logout", AUTH, this::logout);
        r.add("GET", "/api/me", AUTH, this::me);
        r.add("PUT", "/api/profile", AUTH, this::updateProfile);
        r.add("POST", "/api/change-password", AUTH, this::changePassword);
        r.add("GET", "/api/dashboard", AUTH, this::dashboard);

        r.add("GET", "/api/subjects", STUDENT, q -> ApiResponse.ok(catalog.listSubjects(q.user.getId())));
        r.add("GET", "/api/subjects/{id}", STUDENT, q -> ApiResponse.ok(catalog.getSubject(q.id("id"), q.user.getId())));
        r.add("GET", "/api/quizzes", STUDENT, q -> {
            Integer sid = q.queryInt("subject_id");
            return ApiResponse.ok(catalog.listQuizzes(q.user.getId(), sid == null ? null : sid.longValue()));
        });
        r.add("GET", "/api/quizzes/{id}", STUDENT, q -> ApiResponse.ok(catalog.getQuiz(q.id("id"), q.user.getId())));
        r.add("POST", "/api/quizzes/{id}/start", STUDENT, q -> ApiResponse.ok(quizzes.startAttempt(q.user.getId(), q.id("id"))));

        r.add("GET", "/api/attempts", STUDENT, this::listAttempts);
        r.add("GET", "/api/attempts/{id}/questions", STUDENT, q -> ApiResponse.ok(quizzes.getQuestions(q.user.getId(), q.id("id"))));
        r.add("POST", "/api/attempts/{id}/answers", STUDENT, q -> ApiResponse.ok(quizzes.saveAnswer(q.user.getId(), q.id("id"), q.body)));
        r.add("POST", "/api/attempts/{id}/submit", STUDENT, this::submit);
        r.add("GET", "/api/attempts/{id}/result", STUDENT, q -> ApiResponse.ok(quizzes.getResult(q.user.getId(), q.id("id"))));
        r.add("GET", "/api/attempts/{id}/review", STUDENT, q -> ApiResponse.ok(quizzes.getReview(q.user.getId(), q.id("id"))));
        r.add("GET", "/api/analytics", STUDENT, q -> ApiResponse.ok(new StudentReport(db).analytics(q.user.getId())));
        r.add("GET", "/api/leaderboard", STUDENT, q -> ApiResponse.ok(db.fetchAll(
                "SELECT username, attempts, avg_percentage, best_percentage FROM v_leaderboard "
                + "ORDER BY avg_percentage DESC, attempts DESC LIMIT 10")));

        r.add("GET", "/api/admin/students", ADMIN, q -> ApiResponse.ok(admin.students()));
        r.add("GET", "/api/admin/attempts", ADMIN, q -> ApiResponse.ok(admin.attempts()));
        r.add("GET", "/api/admin/topics", ADMIN, q -> ApiResponse.ok(admin.topics()));
        r.add("GET", "/api/admin/analytics", ADMIN, q -> ApiResponse.ok(admin.analytics()));
        r.add("GET", "/api/admin/quizzes", ADMIN, q -> ApiResponse.ok(admin.listQuizzes()));
        r.add("GET", "/api/admin/quizzes/{id}", ADMIN, q -> ApiResponse.ok(admin.getQuiz(q.id("id"))));
        r.add("POST", "/api/admin/quizzes", ADMIN, q -> ApiResponse.created(admin.createQuiz(q.user.getId(), q.body)));
        r.add("PUT", "/api/admin/quizzes/{id}", ADMIN, q -> ApiResponse.ok(admin.updateQuiz(q.id("id"), q.body)));
        r.add("PUT", "/api/admin/quizzes/{id}/questions", ADMIN, q -> ApiResponse.ok(admin.setQuizQuestions(q.id("id"), q.body)));
        r.add("GET", "/api/admin/questions", ADMIN, q -> ApiResponse.ok(admin.listQuestions(q.queryInt("subject_id"), q.query.get("difficulty"))));
        r.add("GET", "/api/admin/questions/{id}", ADMIN, q -> ApiResponse.ok(admin.getQuestion(q.id("id"))));
        r.add("POST", "/api/admin/questions", ADMIN, q -> ApiResponse.created(admin.createQuestion(q.user.getId(), q.body)));
        r.add("PUT", "/api/admin/questions/{id}", ADMIN, q -> ApiResponse.ok(admin.updateQuestion(q.id("id"), q.body)));
        r.add("DELETE", "/api/admin/questions/{id}", ADMIN, q -> ApiResponse.ok(admin.deleteQuestion(q.id("id"))));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String str(Object o) { return o instanceof String s ? s : ""; }

    private String sessionCookie(String value, int maxAge) {
        // HttpOnly: JavaScript cannot read it. SameSite=Strict: never sent on cross-site requests.
        return Application.COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + maxAge
                + (config.cookieSecure() ? "; Secure" : "");
    }

    private ApiResponse register(ApiRequest q) {
        User u = auth.register(q.body);
        return ApiResponse.created(map("user", u.toPublicDict()));
    }

    private ApiResponse login(ApiRequest q) {
        AuthenticationService.LoginResult res = auth.login(str(q.body.get("identifier")), str(q.body.get("password")), q.ip, q.userAgent);
        return ApiResponse.ok(map("user", res.user().toPublicDict(), "home_page", res.user().getHomePage(), "csrf_token", res.csrfToken()))
                .header("Set-Cookie", sessionCookie(res.token(), auth.sessions().ttlSeconds()));
    }

    private ApiResponse logout(ApiRequest q) {
        auth.logout(q.token);
        return ApiResponse.ok(map("logged_out", true)).header("Set-Cookie", sessionCookie("", 0));
    }

    private ApiResponse me(ApiRequest q) {
        // the CSRF token is looked up again so the page can echo it back on state-changing requests
        Row s = db.fetchOne("SELECT csrf_token FROM sessions WHERE id = ?", q.sessionId);
        return ApiResponse.ok(map("user", q.user.toPublicDict(), "csrf_token", s.getStr("csrf_token"),
                "home_page", q.user.getHomePage(), "permissions", new TreeSet<>(q.user.getPermissions())));
    }

    private ApiResponse updateProfile(ApiRequest q) {
        return ApiResponse.ok(map("user", auth.updateProfile(q.user, q.body).toPublicDict()));
    }

    private ApiResponse changePassword(ApiRequest q) {
        auth.changePassword(q.user, q.body, q.sessionId);
        return ApiResponse.ok(map("changed", true));
    }

    private ApiResponse dashboard(ApiRequest q) {
        if (q.user.hasPermission(STUDENT)) quizzes.finalizeExpired(q.user.getId());     // permission-based, not role-based
        Map<String, Object> data = new LinkedHashMap<>(q.user.getDashboardData(db));    // polymorphic call
        data.put("role", q.user.getRole());
        return ApiResponse.ok(data);
    }

    private ApiResponse listAttempts(ApiRequest q) {
        quizzes.finalizeExpired(q.user.getId());
        return ApiResponse.ok(new StudentReport(db).history(q.user.getId(), q.queryInt("subject_id"),
                q.query.get("status"), q.query.getOrDefault("sort", "date"), q.query.getOrDefault("dir", "desc")));
    }

    private ApiResponse submit(ApiRequest q) {
        List<Object> answers = q.body.get("answers") instanceof List<?> l ? new ArrayList<>(l) : null;
        return ApiResponse.ok(quizzes.submit(q.user.getId(), q.id("id"), answers, false));
    }
}
