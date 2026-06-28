package com.visionox.mes.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JWT MVC interceptor anonymous whitelist policy.
 *
 * <p>The project has no Spring-context test harness (every test is a fast Mockito unit test),
 * so instead of booting the app this asserts the path-matching policy directly with the same
 * {@link AntPathMatcher} Spring MVC uses for String exclude patterns. Login and API docs are
 * anonymous; business endpoints require auth. Actuator endpoints are intentionally absent from
 * this whitelist because MVC interceptors do not cover them &mdash; they are gated by
 * {@code ActuatorAccessFilter} instead (see {@link com.visionox.mes.auth.security.ActuatorAccessFilter}).
 */
class WebMvcConfigTest {

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Test
    void loginAndApiDocsRemainAnonymous() {
        assertThat(whitelisted("/v1/auth/login")).isTrue();
        assertThat(whitelisted("/auth/login")).isTrue();
        assertThat(whitelisted("/swagger-ui/index.html")).isTrue();
        assertThat(whitelisted("/v3/api-docs")).isTrue();
        assertThat(whitelisted("/v3/api-docs/swagger-config")).isTrue();
    }

    @Test
    void businessEndpointsStillRequireAuth() {
        assertThat(whitelisted("/v1/orders")).isFalse();
        assertThat(whitelisted("/v1/lots/LOT001/track-in")).isFalse();
        assertThat(whitelisted("/v1/material/location-tasks")).isFalse();
    }

    @Test
    void actuatorEndpointsAreNotInMvcWhitelist() {
        // Actuator is handled by ActuatorAccessFilter, not the MVC interceptor, so none of these
        // should appear here; a stray /actuator/** entry would be misleading and is guarded against.
        assertThat(whitelisted("/actuator/health")).isFalse();
        assertThat(whitelisted("/actuator/info")).isFalse();
        assertThat(whitelisted("/actuator/metrics")).isFalse();
        assertThat(WebMvcConfig.AUTH_WHITELIST)
                .noneMatch(pattern -> pattern.startsWith("/actuator"));
    }

    private boolean whitelisted(String path) {
        return WebMvcConfig.AUTH_WHITELIST.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }
}
