package com.quizsphere;

import com.quizsphere.Errors.AppError;
import com.quizsphere.Errors.NotFound;
import com.quizsphere.Errors.PermissionDenied;
import com.quizsphere.Errors.ValidationError;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transport-independent request pipeline:  route -> authenticate -> CSRF check -> authorize -> handler.
 * Every failure becomes a clean JSON error; stack traces, SQL and secrets are never sent to the browser.
 */
public final class Application {
    private static final Logger LOG = Logger.getLogger("quizsphere.app");
    public static final String COOKIE = "qs_session";
    private static final int MAX_BODY = 256 * 1024;

    public interface Handler { ApiResponse handle(ApiRequest request); }

    private record Route(String method, Pattern pattern, List<String> names, String permission, Handler handler) {}

    /** Tiny router: patterns such as /api/attempts/{id}/submit. */
    public static final class Router {
        private final List<Route> routes = new ArrayList<>();

        public void add(String method, String pattern, String permission, Handler handler) {
            List<String> names = new ArrayList<>();
            Matcher m = Pattern.compile("\\{(\\w+)}").matcher(pattern);
            while (m.find()) names.add(m.group(1));
            String regex = pattern.replaceAll("\\{\\w+}", "([0-9]{1,18})");
            routes.add(new Route(method, Pattern.compile("^" + regex + "$"), names, permission, handler));
        }

        private record Match(Route route, Map<String, String> params) {}

        private Match match(String method, String path) {
            boolean pathKnown = false;
            for (Route r : routes) {
                Matcher m = r.pattern().matcher(path);
                if (!m.matches()) continue;
                pathKnown = true;
                if (!r.method().equals(method)) continue;
                Map<String, String> params = new LinkedHashMap<>();
                for (int i = 0; i < r.names().size(); i++) params.put(r.names().get(i), m.group(i + 1));
                return new Match(r, params);
            }
            if (pathKnown) throw new AppError("Method not allowed.", "method_not_allowed", 405, null);
            throw new NotFound("Unknown API endpoint.");
        }
    }

    private final Router router = new Router();
    private final AuthenticationService auth;

    public Application(Config config, DatabaseManager db) {
        this.auth = new AuthenticationService(db, config);
        QuizGrader grader = new MultipleChoiceGrader();
        new ApiController(config, db, auth, new CatalogService(db), new QuizService(db, grader, config),
                new AdminService(db)).register(router);
    }

    public ApiResponse dispatch(String method, String path, String rawQuery, Function<String, String> header,
                                byte[] body, String ip) {
        try {
            Router.Match m = router.match(method, path);
            ApiRequest req = new ApiRequest(method, path, parseQuery(rawQuery), m.params(), ip, header.apply("User-Agent"));
            if (method.equals("POST") || method.equals("PUT") || method.equals("DELETE")) {
                req.body = parseBody(header.apply("Content-Type"), body);
            }
            if (m.route().permission() != null) {
                String token = cookie(header.apply("Cookie"), COOKIE);
                AuthenticationService.Authenticated a = auth.authenticate(token);      // user comes from the session
                if (!method.equals("GET")) checkCsrf(header.apply("X-CSRF-Token"), a.csrfToken());
                Authorizer.authorize(a.user(), m.route().permission());
                req.user = a.user();
                req.sessionId = a.sessionId();
                req.token = token;
            }
            return m.route().handler().handle(req);
        } catch (AppError e) {
            return ApiResponse.error(e.status(), e.code(), e.getMessage(), e.fields());
        } catch (Json.JsonException e) {
            return ApiResponse.error(400, "bad_json", "The request body is not valid JSON.", null);
        } catch (DatabaseException.Unavailable e) {
            return ApiResponse.error(503, "database_unavailable", e.getMessage(), null);
        } catch (DatabaseException.ConstraintViolation e) {
            return ApiResponse.error(409, "conflict", "The request conflicts with existing data.", null);
        } catch (DatabaseException e) {
            return ApiResponse.error(500, "database_error", "A database error occurred.", null);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unhandled error on " + method + " " + path, e);          // details stay in the log
            return ApiResponse.error(500, "server_error", "Something went wrong on the server.", null);
        }
    }

    private static void checkCsrf(String sent, String expected) {
        if (sent == null || !MessageDigest.isEqual(sent.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
            throw new PermissionDenied("Invalid or missing security token. Reload the page and try again.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBody(String contentType, byte[] body) {
        if (body == null || body.length == 0) return Map.of();
        if (body.length > MAX_BODY) throw new AppError("Request is too large.", "too_large", 413, null);
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new AppError("Content-Type must be application/json.", "unsupported_media_type", 415, null);
        }
        Object parsed = Json.parse(new String(body, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map)) throw new ValidationError("The request body must be a JSON object.");
        return (Map<String, Object>) parsed;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.putIfAbsent(k, v);
        }
        return out;
    }

    private static String cookie(String header, String name) {
        if (header == null) return null;
        for (String part : header.split(";")) {
            String p = part.strip();
            if (p.startsWith(name + "=")) return p.substring(name.length() + 1);
        }
        return null;
    }
}
