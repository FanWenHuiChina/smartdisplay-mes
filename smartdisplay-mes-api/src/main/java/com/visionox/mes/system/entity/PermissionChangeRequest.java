package com.visionox.mes.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限变更申请单。
 */
@Data
@TableName("sys_permission_change_request")
public class PermissionChangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String changeNo;
    private String targetRole;
    private String changeType;
    private String beforeSnapshot;
    private String afterSnapshot;
    private String reason;
    private String status;
    private String requester;
    private String reviewer;
    private String reviewOpinion;
    private LocalDateTime createdTime;
    private LocalDateTime reviewedTime;
    private LocalDateTime updatedTime;
}
