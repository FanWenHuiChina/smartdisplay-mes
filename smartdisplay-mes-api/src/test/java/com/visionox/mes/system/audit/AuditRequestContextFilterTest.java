package com.visionox.mes.system.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRequestContextFilterTest {

    @AfterEach
    void tearDown() {
        AuditRequestContext.clear();
    }

    @Test
    void filterShouldSetAndClearRequestContext() throws Exception {
        AuditRequestContextFilter filter = new AuditRequestContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lots/LOT001/track-in");
        request.setQueryString("mode=manual");
        request.addHeader("X-Forwarded-For", "10.10.1.5, 172.16.0.1");
        request.addHeader("User-Agent", "MES-Console/1.0");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<AuditRequestContext.RequestInfo> seen = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> seen.set(AuditRequestContext.get()));

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().requestMethod()).isEqualTo("POST");
        assertThat(seen.get().requestUri()).isEqualTo("/api/v1/lots/LOT001/track-in?mode=manual");
        assertThat(seen.get().clientIp()).isEqualTo("10.10.1.5");
        assertThat(seen.get().userAgent()).isEqualTo("MES-Console/1.0");
        assertThat(AuditRequestContext.get()).isNull();
    }

    @Test
    void fromShouldLimitLongRequestMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/system/audit-logs");
        request.addHeader("User-Agent", "x".repeat(600));

        AuditRequestContext.RequestInfo requestInfo = AuditRequestContext.from(request);

        assertThat(requestInfo.requestMethod()).hasSizeLessThanOrEqualTo(20);
        assertThat(requestInfo.requestUri()).hasSizeLessThanOrEqualTo(300);
        assertThat(requestInfo.clientIp()).hasSizeLessThanOrEqualTo(80);
        assertThat(requestInfo.userAgent()).hasSize(500);
    }
}
