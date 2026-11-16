package com.quizsphere;

import java.util.Map;

/** Everything a route handler needs to know about one HTTP request. */
public final class ApiRequest {
    public final String method;
    public final String path;
    public final String ip;
    public final String userAgent;
    public final Map<String, String> query;
    public final Map<String, String> params;      // values of {id}-style parts of the URL
    public Map<String, Object> body = Map.of();
    public User user;                             // set by the server from the SESSION, never from the browser
    public long sessionId;
    public String token;

    public ApiRequest(String method, String path, Map<String, String> query, Map<String, String> params,
                      String ip, String userAgent) {
        this.method = method;
        this.path = path;
        this.query = query;
        this.params = params;
        this.ip = ip == null ? "unknown" : ip;
        this.userAgent = userAgent;
    }

    public long id(String name) { return Long.parseLong(params.get(name)); }

    public Integer queryInt(String name) {
        String v = query.get(name);
        if (v == null || v.isBlank()) return null;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
    }
}
