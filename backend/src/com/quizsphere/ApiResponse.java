package com.quizsphere;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consistent JSON envelope:  {"ok":true,"data":...}  or  {"ok":false,"error":{code,message,fields}}. */
public final class ApiResponse {
    public final int status;
    private final Map<String, Object> envelope = new LinkedHashMap<>();
    public final List<String[]> headers = new ArrayList<>();

    private ApiResponse(int status) { this.status = status; }

    public static ApiResponse ok(Object data) { return of(200, data); }

    public static ApiResponse created(Object data) { return of(201, data); }

    private static ApiResponse of(int status, Object data) {
        ApiResponse r = new ApiResponse(status);
        r.envelope.put("ok", true);
        r.envelope.put("data", data);
        return r;
    }

    public static ApiResponse error(int status, String code, String message, Map<String, String> fields) {
        ApiResponse r = new ApiResponse(status);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("code", code);
        err.put("message", message);
        err.put("fields", fields == null ? Map.of() : fields);
        r.envelope.put("ok", false);
        r.envelope.put("error", err);
        return r;
    }

    public ApiResponse header(String name, String value) {
        headers.add(new String[]{name, value});
        return this;
    }

    public String toJson() { return Json.stringify(envelope); }
}
