package com.visionox.mes.system.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为审计日志绑定请求元数据，并确保请求结束后清理线程上下文。
 */
@Component
public class AuditRequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        AuditRequestContext.set(AuditRequestContext.from(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuditRequestContext.clear();
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    /**
     * Honor an upstream X-Request-Id (e.g. propagated by Nginx) when present, otherwise
     * generate a short correlation id for this request.
     */
    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            String trimmed = incoming.trim();
            return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
