package com.visionox.mes.system.service;

import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.auth.security.AuthUser;
import com.visionox.mes.system.audit.AuditRequestContext;
import com.visionox.mes.system.entity.AuditLog;
import com.visionox.mes.system.mapper.AuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogMapper);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        AuditRequestContext.clear();
    }

    @Test
    void recordShouldPersistRequestContextWhenPresent() {
        AuthContext.set(new AuthUser("op1001", "OPERATOR"));
        AuditRequestContext.set(new AuditRequestContext.RequestInfo(
                "POST",
                "/api/v1/lots/LOT001/track-in",
                "10.10.1.5",
                "MES-Console/1.0"
        ));

        auditLogService.record("TRACK_IN", "LOT001", "LOT", "Lot进站", null, "smartdisplay-mes-api", "{\"lotNo\":\"LOT001\"}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getOperator()).isEqualTo("op1001");
        assertThat(saved.getResult()).isEqualTo("SUCCESS");
        assertThat(saved.getRequestMethod()).isEqualTo("POST");
        assertThat(saved.getRequestUri()).isEqualTo("/api/v1/lots/LOT001/track-in");
        assertThat(saved.getClientIp()).isEqualTo("10.10.1.5");
        assertThat(saved.getUserAgent()).isEqualTo("MES-Console/1.0");
        assertThat(saved.getCreatedTime()).isNotNull();
    }

    @Test
    void recordShouldWorkWithoutRequestContext() {
        auditLogService.record("ORDER_RELEASE", "MO001", "ORDER", "工单释放", "", "smartdisplay-mes-api", "{}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getOperator()).isEqualTo("system");
        assertThat(saved.getRequestMethod()).isNull();
        assertThat(saved.getRequestUri()).isNull();
        assertThat(saved.getClientIp()).isNull();
        assertThat(saved.getUserAgent()).isNull();
    }

    @Test
    void recordFailureShouldPersistFailResultAndRequestContext() {
        AuditRequestContext.set(new AuditRequestContext.RequestInfo(
                "POST",
                "/api/v1/orders/MO001/release",
                "10.10.1.8",
                "MES-Console/1.0"
        ));

        auditLogService.recordFailure("ORDER_RELEASE", "MO001", "ORDER", "操作失败", null, "smartdisplay-mes-api", "{\"code\":400}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getResult()).isEqualTo("FAIL");
        assertThat(saved.getOperator()).isEqualTo("system");
        assertThat(saved.getRequestMethod()).isEqualTo("POST");
        assertThat(saved.getRequestUri()).isEqualTo("/api/v1/orders/MO001/release");
        assertThat(saved.getClientIp()).isEqualTo("10.10.1.8");
    }
}
