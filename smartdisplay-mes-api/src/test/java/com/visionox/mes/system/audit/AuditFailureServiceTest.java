package com.visionox.mes.system.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditFailureServiceTest {

    @Mock
    private AuditLogService auditLogService;

    private AuditFailureService auditFailureService;

    @BeforeEach
    void setUp() {
        auditFailureService = new AuditFailureService(new AuditFailureResolver(), auditLogService, new ObjectMapper());
    }

    @Test
    void recordShouldWriteFailureAuditForKeyWriteRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders/MO001/release");

        auditFailureService.record(request, "BusinessException", 400, "Route校验失败");

        verify(auditLogService).recordFailure(
                eq("ORDER_RELEASE"),
                eq("MO001"),
                eq("ORDER"),
                contains("操作失败"),
                isNull(),
                eq("smartdisplay-mes-api"),
                contains("\"errorType\":\"BusinessException\"")
        );
    }

    @Test
    void recordShouldIgnoreNonKeyReadRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/dashboard/overview");

        auditFailureService.record(request, "BusinessException", 400, "无权限");

        verify(auditLogService, never()).recordFailure(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
