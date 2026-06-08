package com.visionox.mes.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统审计日志。
 */
@Data
@TableName("sys_audit_log")
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String action;
    private String bizNo;
    private String bizType;
    private String description;
    private String operator;
    private String result;
    private String source;
    private String requestSnapshot;
    private String requestMethod;
    private String requestUri;
    private String clientIp;
    private String userAgent;
    private LocalDateTime createdTime;
}
