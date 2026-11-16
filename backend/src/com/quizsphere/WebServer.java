package com.quizsphere;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Java's built-in HTTP server: serves the frontend files and hands /api/* requests to Application. */
public final class WebServer {
    private static final Logger LOG = Logger.getLogger("quizsphere.web");
    private static final int MAX_BODY = 256 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            ".html", "text/html; charset=utf-8", ".css", "text/css; charset=utf-8",
            ".js", "text/javascript; charset=utf-8", ".svg", "image/svg+xml",
            ".ico", "image/x-icon", ".png", "image/png");

    private final HttpServer server;
    private final Application app;
    private final Path root;

    public WebServer(Config config, Application app, Path frontendDir) throws IOException {
        this.app = app;
        this.root = frontendDir.toAbsolutePath().normalize();
        this.server = HttpServer.create(new InetSocketAddress(config.serverHost(), config.serverPort()), 0);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(Executors.newFixedThreadPool(16));
    }

    public void start() { server.start(); }

    public void stop() { server.stop(1); }

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            if (path.startsWith("/api/")) {
                byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) {
                    sendApi(ex, ApiResponse.error(413, "too_large", "Request is too large.", null));
                    return;
                }
                String ip = ex.getRemoteAddress().getAddress().getHostAddress();
                sendApi(ex, app.dispatch(method, path, ex.getRequestURI().getRawQuery(),
                        name -> ex.getRequestHeaders().getFirst(name), body, ip));
            } else if (method.equals("GET") || method.equals("HEAD")) {
                sendStatic(ex, path, method.equals("HEAD"));
            } else {
                sendBytes(ex, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8), false);
            }
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Request failed", t);
            try { sendBytes(ex, 500, "text/plain; charset=utf-8", "Server error".getBytes(StandardCharsets.UTF_8), false); }
            catch (IOException ignored) { /* client already gone */ }
        } finally {
            ex.close();
        }
    }

    private void sendApi(HttpExchange ex, ApiResponse r) throws IOException {
        for (String[] h : r.headers) ex.getResponseHeaders().add(h[0], h[1]);
        sendBytes(ex, r.status, "application/json; charset=utf-8", r.toJson().getBytes(StandardCharsets.UTF_8), false);
    }

    private void sendStatic(HttpExchange ex, String urlPath, boolean headOnly) throws IOException {
        if (urlPath.equals("/")) urlPath = "/index.html";
        Path file = root.resolve(urlPath.substring(1)).normalize();
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null : TYPES.get(name.substring(dot).toLowerCase());
        if (urlPath.contains("\0") || !file.startsWith(root) || type == null || !Files.isRegularFile(file)) {
            sendBytes(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8), headOnly);
            return;
        }
        sendBytes(ex, 200, type, Files.readAllBytes(file), headOnly);
    }

    private void sendBytes(HttpExchange ex, int status, String type, byte[] bytes, boolean headOnly) throws IOException {
        var h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("Cache-Control", type.startsWith("application/json") ? "no-store" : "no-cache");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
        h.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
        if (headOnly || bytes.length == 0) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
    }
}
