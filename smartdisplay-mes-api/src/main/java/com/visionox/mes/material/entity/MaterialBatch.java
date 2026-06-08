package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料批次库存。
 */
@Data
@TableName("material_batch")
public class MaterialBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialCode;

    private String materialName;

    private String batchNo;

    private String supplierCode;

    private BigDecimal totalQty;

    private BigDecimal availableQty;

    private BigDecimal reservedQty;

    private BigDecimal consumedQty;

    private BigDecimal frozenQty;

    private BigDecimal returnedQty;

    private String unit;

    private String status;

    private String qualityStatus;

    private LocalDateTime receivedTime;

    private LocalDateTime expireTime;

    private String location;

    private Integer fifoSeq;

    private LocalDateTime lastCountTime;

    private Long stockVersion;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
