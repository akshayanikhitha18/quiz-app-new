package com.quizsphere;

import com.quizsphere.Errors.AuthenticationError;
import com.quizsphere.Errors.PermissionDenied;

/** Server-side authorization: decides whether a user may use a route. */
public final class Authorizer {
    private Authorizer() { }

    /** Route marker: any logged-in user. */
    public static final String AUTHENTICATED = "authenticated";

    public static void authorize(User user, String permission) {
        if (permission == null) return;                          // public route
        if (user == null) throw new AuthenticationError("Please log in to continue.");
        if (AUTHENTICATED.equals(permission)) return;
        if (!user.hasPermission(permission)) {                   // polymorphic: depends on Student/Admin
            throw new PermissionDenied("You do not have access to this resource.");
        }
    }
}
