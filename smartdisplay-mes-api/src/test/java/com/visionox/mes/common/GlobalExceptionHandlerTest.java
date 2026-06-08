package com.visionox.mes.common;

import com.visionox.mes.system.audit.AuditFailureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private AuditFailureService auditFailureService;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(auditFailureService);
    }

    @Test
    void handleBusinessExceptionShouldRecordFailureAuditAndKeepResponseShape() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lots/LOT001/track-in");

        Result<Void> result = handler.handleBusinessException(new BusinessException(403, "当前角色无权执行该操作"), request);

        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMessage()).isEqualTo("当前角色无权执行该操作");
        verify(auditFailureService).record(same(request), eq("BusinessException"), eq(403), eq("当前角色无权执行该操作"));
    }
}
