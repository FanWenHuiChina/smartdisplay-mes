package com.visionox.mes.system.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计请求上下文。
 */
public final class AuditRequestContext {

    private static final ThreadLocal<RequestInfo> CURRENT = new ThreadLocal<>();

    private AuditRequestContext() {
    }

    public static void set(RequestInfo requestInfo) {
        if (requestInfo == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(requestInfo);
    }

    public static RequestInfo get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static RequestInfo from(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return new RequestInfo(
                limit(request.getMethod(), 20),
                limit(requestUri(request), 300),
                limit(clientIp(request), 80),
                limit(request.getHeader("User-Agent"), 500)
        );
    }

    private static String requestUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return uri;
        }
        return uri + "?" + queryString;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = firstValidHeader(request,
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP");
        if (forwardedFor != null && forwardedFor.contains(",")) {
            forwardedFor = forwardedFor.substring(0, forwardedFor.indexOf(',')).trim();
        }
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor;
        }
        return request.getRemoteAddr();
    }

    private static String firstValidHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record RequestInfo(String requestMethod, String requestUri, String clientIp, String userAgent) {
    }
}
