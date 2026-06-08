package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 供应商 8D 整改闭环。
 */
@Data
@TableName("supplier_corrective_action")
public class SupplierCorrectiveAction implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String actionNo;
    private String supplierCode;
    private String sourceType;
    private String sourceNo;
    private String issueSummary;
    private String rootCause;
    private String containmentAction;
    private String correctiveAction;
    private String preventiveAction;
    private String owner;
    private String severity;
    private String status;
    private LocalDateTime dueTime;
    private LocalDateTime closedTime;
    private String verificationResult;
    private String requestSnapshot;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
