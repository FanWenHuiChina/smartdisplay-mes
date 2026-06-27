package com.visionox.mes.system.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuditRequestContextFilterTest {

    @AfterEach
    void tearDown() {
        AuditRequestContext.clear();
        MDC.clear();
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
        AtomicReference<String> mdcRequestId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            seen.set(AuditRequestContext.get());
            mdcRequestId.set(MDC.get(AuditRequestContextFilter.REQUEST_ID_MDC_KEY));
        });

        assertThat(seen.get()).isNotNull();
        assertThat(seen.get().requestMethod()).isEqualTo("POST");
        assertThat(seen.get().requestUri()).isEqualTo("/api/v1/lots/LOT001/track-in?mode=manual");
        assertThat(seen.get().clientIp()).isEqualTo("10.10.1.5");
        assertThat(seen.get().userAgent()).isEqualTo("MES-Console/1.0");
        assertThat(AuditRequestContext.get()).isNull();

        // A requestId is bound to MDC during the request and echoed back in the response header,
        // then cleared from MDC afterwards so it cannot leak into the next request on this thread.
        assertThat(mdcRequestId.get()).isNotBlank();
        assertThat(response.getHeader(AuditRequestContextFilter.REQUEST_ID_HEADER)).isEqualTo(mdcRequestId.get());
        assertThat(MDC.get(AuditRequestContextFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void filterShouldPropagateIncomingRequestId() throws Exception {
        AuditRequestContextFilter filter = new AuditRequestContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lots");
        request.addHeader(AuditRequestContextFilter.REQUEST_ID_HEADER, "upstream-trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcRequestId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                mdcRequestId.set(MDC.get(AuditRequestContextFilter.REQUEST_ID_MDC_KEY)));

        // An upstream-provided X-Request-Id (e.g. from Nginx) is honored rather than regenerated.
        assertThat(mdcRequestId.get()).isEqualTo("upstream-trace-123");
        assertThat(response.getHeader(AuditRequestContextFilter.REQUEST_ID_HEADER)).isEqualTo("upstream-trace-123");
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
