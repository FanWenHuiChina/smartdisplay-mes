package com.visionox.mes.auth.security;

/**
 * 请求级用户上下文。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthUser> CURRENT_USER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        CURRENT_USER.set(user);
    }

    public static AuthUser get() {
        return CURRENT_USER.get();
    }

    public static String username() {
        AuthUser user = get();
        return user == null ? "system" : user.getUsername();
    }

    public static String role() {
        AuthUser user = get();
        return user == null ? "SYSTEM" : user.getRole();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
