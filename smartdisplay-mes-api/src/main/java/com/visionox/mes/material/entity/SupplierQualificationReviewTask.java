package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商准入复审任务。
 */
@Data
@TableName("supplier_qualification_review_task")
public class SupplierQualificationReviewTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;
    private String supplierCode;
    private String reviewType;
    private String sourceNo;
    private String triggerReason;
    private String qualificationBefore;
    private String riskBefore;
    private String suggestedQualification;
    private String suggestedRisk;
    private String reviewStatus;
    private LocalDateTime dueTime;
    private String reviewer;
    private LocalDateTime reviewTime;
    private String decision;
    private String decisionComment;
    private String performanceSnapshot;
    private String requestSnapshot;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
