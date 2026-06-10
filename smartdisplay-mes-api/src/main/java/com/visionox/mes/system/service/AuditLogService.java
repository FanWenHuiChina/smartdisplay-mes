package com.visionox.mes.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    public Page<AuditLog> page(long current,
                               long size,
                               String bizNo,
                               String action,
                               String result,
                               String source,
                               String operator,
                               LocalDateTime startTime,
                               LocalDateTime endTime) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (hasText(bizNo)) {
            wrapper.like(AuditLog::getBizNo, bizNo.trim());
        }
        applyActionFilter(wrapper, action);
        if (hasText(result)) {
            wrapper.eq(AuditLog::getResult, result.trim().toUpperCase());
        }
        if (hasText(source)) {
            wrapper.like(AuditLog::getSource, source.trim());
        }
        if (hasText(operator)) {
            wrapper.like(AuditLog::getOperator, operator.trim());
        }
        if (startTime != null) {
            wrapper.ge(AuditLog::getCreatedTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(AuditLog::getCreatedTime, endTime);
        }
        wrapper.orderByDesc(AuditLog::getCreatedTime);
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.min(Math.max(1, size), 100);
        return auditLogMapper.selectPage(new Page<>(safeCurrent, safeSize), wrapper);
    }

    private void applyActionFilter(LambdaQueryWrapper<AuditLog> wrapper, String action) {
        if (!hasText(action)) {
            return;
        }
        String normalized = action.trim().toUpperCase();
        switch (normalized) {
            case "TRACK" -> wrapper.like(AuditLog::getAction, "TRACK");
            case "LOT" -> wrapper.and(group -> group
                    .like(AuditLog::getAction, "LOT")
                    .or().like(AuditLog::getAction, "HOLD")
                    .or().like(AuditLog::getAction, "RELEASE")
                    .or().like(AuditLog::getAction, "REWORK")
                    .or().like(AuditLog::getAction, "SCRAP"));
            case "ORDER" -> wrapper.like(AuditLog::getAction, "ORDER");
            case "AI" -> wrapper.like(AuditLog::getAction, "AI");
            case "RECIPE" -> wrapper.like(AuditLog::getAction, "RECIPE");
            case "WMS" -> wrapper.and(group -> group
                    .like(AuditLog::getAction, "WMS")
                    .or().like(AuditLog::getAction, "MATERIAL_LOCATION_TASK")
                    .or().like(AuditLog::getAction, "MATERIAL_"));
            case "QMS" -> wrapper.and(group -> group
                    .like(AuditLog::getAction, "QMS")
                    .or().like(AuditLog::getAction, "QUALITY_"));
            case "EAP" -> wrapper.and(group -> group
                    .like(AuditLog::getAction, "EAP")
                    .or().like(AuditLog::getAction, "EQUIPMENT_"));
            default -> wrapper.like(AuditLog::getAction, normalized);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
