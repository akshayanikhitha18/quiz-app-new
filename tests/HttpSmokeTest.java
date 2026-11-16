import com.quizsphere.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.util.*;

public class HttpSmokeTest {
    static int fails = 0;
    static void check(boolean ok, String name) { System.out.println((ok ? "PASS " : "FAIL ") + name); if (!ok) fails++; }
    static HttpResponse<String> send(HttpClient c, String method, String path, String body, String ctype) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18080" + path));
        if (body != null) { b.method(method, HttpRequest.BodyPublishers.ofString(body)); if (ctype != null) b.header("Content-Type", ctype); }
        else b.method(method, HttpRequest.BodyPublishers.noBody());
        return c.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    public static void main(String[] a) throws Exception {
        Config cfg = new Config("127.0.0.1", 1, "u", "p", "quizsphere", 2, "127.0.0.1", 18080, 8, 100000, 5, 15, false, 5);
        WebServer ws = new WebServer(cfg, new Application(cfg, new DatabaseManager(cfg)), Path.of("frontend"));
        ws.start();
        HttpClient c = HttpClient.newHttpClient();
        try {
            var r = send(c, "GET", "/", null, null);
            check(r.statusCode() == 200 && r.body().contains("QuizSphere"), "landing page served");
            check(r.headers().firstValue("Content-Type").orElse("").startsWith("text/html"), "html content type");
            check(r.headers().firstValue("Content-Security-Policy").orElse("").contains("script-src 'self'"), "CSP header present");
            r = send(c, "GET", "/js/api.js", null, null);
            check(r.statusCode() == 200 && r.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"), "js served as text/javascript");
            for (String p : List.of("/login.html","/register.html","/dashboard.html","/subjects.html","/subject-detail.html","/quiz-instructions.html","/quiz.html","/results.html","/review.html","/history.html","/analytics.html","/profile.html","/admin-dashboard.html","/admin-quizzes.html","/admin-questions.html","/css/style.css","/css/auth.css","/css/dashboard.css","/css/quiz.css","/css/admin.css"))
                check(send(c, "GET", p, null, null).statusCode() == 200, "serves " + p);
            check(send(c, "GET", "/../backend/src/com/quizsphere/Main.java", null, null).statusCode() == 404, "path traversal blocked");
            check(send(c, "GET", "/.env", null, null).statusCode() == 404, ".env not served");
            check(send(c, "GET", "/schema.sql", null, null).statusCode() == 404, "sql not served");
            r = send(c, "GET", "/api/me", null, null);
            check(r.statusCode() == 401 && r.body().contains("\"ok\":false") && r.body().contains("unauthenticated"), "/api/me without session -> 401 JSON");
            r = send(c, "GET", "/api/admin/students", null, null);
            check(r.statusCode() == 401, "admin route without session -> 401");
            r = send(c, "POST", "/api/quizzes/1/start", "{}", "application/json");
            check(r.statusCode() == 401, "state-changing route without session -> 401");
            r = send(c, "GET", "/api/nope", null, null);
            check(r.statusCode() == 404, "unknown endpoint -> 404");
            r = send(c, "DELETE", "/api/me", null, null);
            check(r.statusCode() == 405, "wrong method -> 405");
            r = send(c, "POST", "/api/login", "identifier=a&password=b", "text/plain");
            check(r.statusCode() == 415, "non-JSON content type rejected");
            r = send(c, "POST", "/api/login", "{bad json", "application/json");
            check(r.statusCode() == 400 && r.body().contains("bad_json"), "malformed JSON -> 400");
            r = send(c, "POST", "/api/register", "{\"full_name\":\"A\",\"username\":\"x\",\"email\":\"bad\",\"password\":\"1\",\"confirm_password\":\"2\"}", "application/json");
            check(r.statusCode() == 422 && r.body().contains("\"fields\"") && r.body().contains("username"), "register validation -> 422 with field errors");
            r = send(c, "GET", "/api/public/subjects", null, null);
            check(r.statusCode() == 503 && !r.body().contains("jdbc") && !r.body().contains("Exception"), "DB down -> clean 503, no internals leaked");
        } finally { ws.stop(); }
        Object j = Json.parse("{\"a\":[1,2.5,\"x\\n\\u00e9\"],\"b\":{\"c\":null,\"d\":true}}");
        check(Json.stringify(j).equals("{\"a\":[1,2.5,\"x\\n\u00e9\"],\"b\":{\"c\":null,\"d\":true}}"), "JSON round trip");
        check(Json.stringify(Map.of("t", java.time.LocalDateTime.of(2026,1,2,3,4,5))).equals("{\"t\":\"2026-01-02T03:04:05Z\"}"), "datetime as UTC ISO");
        check(Json.stringify("<script>").equals("\"\\u003cscript\\u003e\""), "angle brackets escaped");
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails);
    }
}
