package com.visionox.mes.system.audit;

/**
 * 失败审计目标。
 */
public record AuditFailureTarget(String action, String bizNo, String bizType) {
}
