package com.visionox.mes.system.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为审计日志绑定请求元数据，并确保请求结束后清理线程上下文。
 */
@Component
public class AuditRequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        AuditRequestContext.set(AuditRequestContext.from(request));
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuditRequestContext.clear();
        }
    }
}
