package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商主数据与准入状态。
 */
@Data
@TableName("md_supplier")
public class Supplier implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String supplierCode;
    private String supplierName;
    private String supplierType;
    private String materialClass;
    private String qualificationStatus;
    private String riskLevel;
    private BigDecimal score;
    private BigDecimal passRate;
    private String owner;
    private LocalDateTime lastAuditTime;
    private LocalDateTime nextAuditDue;
    private String status;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
