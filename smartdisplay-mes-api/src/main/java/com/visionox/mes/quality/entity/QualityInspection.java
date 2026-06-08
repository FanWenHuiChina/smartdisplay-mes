package com.visionox.mes.quality.entity;

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
 * 质量检验记录。
 */
@Data
@TableName("quality_inspection")
public class QualityInspection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inspectionNo;

    private String lotNo;

    private String orderNo;

    private String productCode;

    private String stepCode;

    private String equipmentCode;

    private String recipeCode;

    private String itemCode;

    private String itemName;

    private BigDecimal measuredValue;

    private BigDecimal upperLimit;

    private BigDecimal lowerLimit;

    private String unit;

    private String result;

    private String defectCode;

    private String defectPosition;

    private String inspector;

    private LocalDateTime inspectionTime;

    private String source;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
