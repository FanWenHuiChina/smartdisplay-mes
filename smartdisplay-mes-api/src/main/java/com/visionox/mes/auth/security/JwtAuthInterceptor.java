package com.visionox.mes.auth.security;

import com.visionox.mes.auth.util.JwtUtil;
import com.visionox.mes.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT认证与轻量级RBAC拦截器。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RolePermissionService rolePermissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new BusinessException(401, "未登录或登录已过期");
        }

        String username = jwtUtil.getUsernameFromToken(token);
        String role = rolePermissionService.normalizeRole(jwtUtil.getRoleFromToken(token));
        AuthContext.set(new AuthUser(username, role));

        if (!rolePermissionService.canAccess(role, request)) {
            AuthContext.clear();
            throw new BusinessException(403, "当前角色无权执行该操作");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (!authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
