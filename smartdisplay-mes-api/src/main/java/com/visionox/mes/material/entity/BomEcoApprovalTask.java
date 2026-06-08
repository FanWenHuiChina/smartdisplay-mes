package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * BOM/ECO跨部门会签任务。
 */
@Data
@TableName("md_bom_eco_approval_task")
public class BomEcoApprovalTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;
    private String changeNo;
    private String ecoNo;
    private String productCode;
    private String targetBomCode;
    private String approvalRole;
    private String approver;
    private String approvalStatus;
    private String decision;
    private String opinion;
    private String slaLevel;
    private Integer slaHours;
    private LocalDateTime dueTime;
    private LocalDateTime actionTime;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
