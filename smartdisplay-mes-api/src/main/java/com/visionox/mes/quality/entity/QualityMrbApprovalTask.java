package com.visionox.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MRB跨部门会签任务。
 */
@Data
@TableName("quality_mrb_approval_task")
public class QualityMrbApprovalTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;
    private String mrbNo;
    private String eventNo;
    private String lotNo;
    private String approvalRole;
    private String approver;
    private String approvalStatus;
    private String decision;
    private String opinion;
    private LocalDateTime dueTime;
    private LocalDateTime actionTime;
    private String slaLevel;
    private Integer slaHours;
    private String escalationRole;
    private String escalatedTo;
    private LocalDateTime escalatedTime;
    private String escalationReason;
    private Integer escalationCount;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
