package com.visionox.mes.system.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionox.mes.system.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 关键写接口失败审计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditFailureService {

    private static final int MAX_MESSAGE_LENGTH = 300;

    private final AuditFailureResolver auditFailureResolver;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public void record(HttpServletRequest request, String errorType, Integer code, String message) {
        auditFailureResolver.resolve(request).ifPresent(target -> recordFailure(target, errorType, code, message));
    }

    private void recordFailure(AuditFailureTarget target, String errorType, Integer code, String message) {
        try {
            auditLogService.recordFailure(
                    target.action(),
                    target.bizNo(),
                    target.bizType(),
                    "操作失败: " + limit(message, MAX_MESSAGE_LENGTH),
                    null,
                    "smartdisplay-mes-api",
                    failureSnapshot(errorType, code, message)
            );
        } catch (Exception auditException) {
            log.warn("失败审计写入失败: action={}, bizNo={}", target.action(), target.bizNo(), auditException);
        }
    }

    private String failureSnapshot(String errorType, Integer code, String message) throws Exception {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("errorType", errorType);
        snapshot.put("code", code);
        snapshot.put("message", limit(message, MAX_MESSAGE_LENGTH));
        return objectMapper.writeValueAsString(snapshot);
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
