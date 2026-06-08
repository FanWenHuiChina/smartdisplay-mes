package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * BOM变更申请单。
 */
@Data
@TableName("md_bom_change_request")
public class BomChangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String changeNo;

    private String changeType;

    private String productCode;

    private String sourceBomCode;

    private String targetBomCode;

    private Long targetBomId;

    private String targetVersion;

    private String status;

    private String reason;

    private String beforeSnapshot;

    private String afterSnapshot;

    private String substitutePolicySnapshot;

    private String requestedBy;

    private LocalDateTime requestedTime;

    private String reviewedBy;

    private LocalDateTime reviewedTime;

    private String reviewComment;

    private String publishedBy;

    private LocalDateTime publishedTime;

    private String ecoNo;

    private String ecoRiskLevel;

    private String ecoPackageSnapshot;

    private String ecoApprovalStatus;

    private String ecoRequiredRoles;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
