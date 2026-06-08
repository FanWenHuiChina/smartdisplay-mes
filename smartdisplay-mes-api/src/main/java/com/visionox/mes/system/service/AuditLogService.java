package com.visionox.mes.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.system.audit.AuditRequestContext;
import com.visionox.mes.system.entity.AuditLog;
import com.visionox.mes.system.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志服务。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String bizNo, String bizType, String description, String operator, String source, String requestSnapshot) {
        insert(action, bizNo, bizType, description, operator, source, requestSnapshot, "SUCCESS");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String bizNo, String bizType, String description, String operator, String source, String requestSnapshot) {
        insert(action, bizNo, bizType, description, operator, source, requestSnapshot, "FAIL");
    }

    private void insert(String action, String bizNo, String bizType, String description, String operator, String source, String requestSnapshot, String result) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setBizNo(bizNo);
        log.setBizType(bizType);
        log.setDescription(description);
        log.setOperator(operator == null || operator.isBlank() ? AuthContext.username() : operator);
        log.setResult(result);
        log.setSource(source);
        log.setRequestSnapshot(requestSnapshot);
        AuditRequestContext.RequestInfo requestInfo = AuditRequestContext.get();
        if (requestInfo != null) {
            log.setRequestMethod(requestInfo.requestMethod());
            log.setRequestUri(requestInfo.requestUri());
            log.setClientIp(requestInfo.clientIp());
            log.setUserAgent(requestInfo.userAgent());
        }
        log.setCreatedTime(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    public List<AuditLog> list(String bizNo, int limit) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (bizNo != null && !bizNo.isBlank()) {
            wrapper.eq(AuditLog::getBizNo, bizNo);
        }
        wrapper.orderByDesc(AuditLog::getCreatedTime).last("LIMIT " + Math.max(1, limit));
        return auditLogMapper.selectList(wrapper);
    }
}
