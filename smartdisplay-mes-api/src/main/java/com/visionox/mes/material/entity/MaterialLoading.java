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
 * Lot进站上料锁定记录。
 */
@Data
@TableName("material_loading")
public class MaterialLoading implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String loadingNo;

    private String lotNo;

    private String orderNo;

    private String productCode;

    private String stepCode;

    private String equipmentCode;

    private String materialCode;

    private String materialName;

    private String batchNo;

    private BigDecimal requiredQty;

    private BigDecimal loadedQty;

    private String unit;

    private String status;

    private String operator;

    private LocalDateTime loadedTime;

    private LocalDateTime consumedTime;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
