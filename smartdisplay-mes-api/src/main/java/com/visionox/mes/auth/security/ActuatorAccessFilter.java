package com.visionox.mes.auth.security;

import com.visionox.mes.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 保护 Actuator 管理端点的 servlet 过滤器。
 *
 * <p>{@code health} 与 {@code info} 对匿名开放，供容器存活/就绪探针和监控直接访问；
 * 其余端点（如 {@code metrics}）必须携带有效 JWT 才能访问，避免运行指标对外泄露。</p>
 *
 * <p>之所以用 servlet 过滤器而非复用 {@code JwtAuthInterceptor}：Spring MVC 的
 * HandlerInterceptor 不会作用于 Actuator 独立的 endpoint handler mapping，过滤器才能
 * 真正拦到 {@code /actuator/**} 请求。</p>
 */
@Component
@Order(0)
@RequiredArgsConstructor
public class ActuatorAccessFilter extends OncePerRequestFilter {

    static final String ACTUATOR_PREFIX = "/actuator";
    static final List<String> ANONYMOUS_ACTUATOR_PREFIXES = List.of("/actuator/health", "/actuator/info");

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = pathWithinApp(request);
        if (path.startsWith(ACTUATOR_PREFIX) && !isAnonymousActuator(path) && !hasValidToken(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Actuator endpoint requires authentication\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAnonymousActuator(String path) {
        return ANONYMOUS_ACTUATOR_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean hasValidToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        return jwtUtil.validateToken(authorization.substring(7));
    }

    private String pathWithinApp(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
