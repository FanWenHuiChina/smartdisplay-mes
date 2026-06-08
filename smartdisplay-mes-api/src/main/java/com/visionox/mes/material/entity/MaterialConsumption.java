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
 * 物料消耗追溯记录。
 */
@Data
@TableName("material_consumption")
public class MaterialConsumption implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String consumptionNo;

    private String lotNo;

    private String orderNo;

    private String productCode;

    private String stepCode;

    private String equipmentCode;

    private String materialCode;

    private String materialName;

    private String batchNo;

    private BigDecimal consumedQty;

    private String unit;

    private String operator;

    private LocalDateTime consumeTime;

    private Long stepRecordId;

    private String traceStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
