package com.visionox.mes.auth.security;

import com.visionox.mes.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that ActuatorAccessFilter keeps health/info anonymous (for container probes and
 * monitoring) while requiring a valid JWT for every other actuator endpoint, and never
 * interferes with non-actuator requests.
 */
class ActuatorAccessFilterTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final ActuatorAccessFilter filter = new ActuatorAccessFilter(jwtUtil);

    @Test
    void healthProbeIsAnonymous() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(actuatorRequest("/api/actuator/health"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void readinessProbeIsAnonymous() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(actuatorRequest("/api/actuator/health/readiness"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void infoIsAnonymous() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(actuatorRequest("/api/actuator/info"), response, chain);

        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void metricsWithoutTokenIsRejected() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(actuatorRequest("/api/actuator/metrics"), response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void metricsWithValidTokenIsAllowed() throws Exception {
        when(jwtUtil.validateToken("good-token")).thenReturn(true);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = actuatorRequest("/api/actuator/metrics");
        request.addHeader("Authorization", "Bearer good-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void metricsWithInvalidTokenIsRejected() throws Exception {
        when(jwtUtil.validateToken("bad-token")).thenReturn(false);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = actuatorRequest("/api/actuator/metrics");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void nonActuatorRequestIsNotTouched() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(actuatorRequest("/api/v1/orders"), response, chain);

        // Business endpoints are guarded by the MVC interceptor; this filter must let them pass.
        verify(chain, times(1)).doFilter(any(), any());
    }

    private MockHttpServletRequest actuatorRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("/api");
        return request;
    }
}
