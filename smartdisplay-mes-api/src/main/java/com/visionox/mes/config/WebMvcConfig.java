package com.visionox.mes.config;

import com.visionox.mes.auth.security.JwtAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC配置。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Anonymous whitelist for the JWT MVC interceptor: login, API docs and the error page.
     *
     * Actuator endpoints are deliberately NOT listed here. Spring MVC HandlerInterceptors are
     * not applied to Actuator's endpoint handler mapping, so actuator access is gated separately
     * by {@link com.visionox.mes.auth.security.ActuatorAccessFilter} (a servlet filter that keeps
     * health/info anonymous for container probes while requiring a JWT for everything else).
     */
    static final List<String> AUTH_WHITELIST = List.of(
            "/auth/login",
            "/v1/auth/login",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/error"
    );

    private final JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(AUTH_WHITELIST);
    }
}
