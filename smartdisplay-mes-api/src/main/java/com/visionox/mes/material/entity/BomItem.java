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
 * BOM物料明细。
 */
@Data
@TableName("md_bom_item")
public class BomItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bomId;

    private String materialCode;

    private String materialName;

    private String stepCode;

    private BigDecimal requiredQty;

    private String unit;

    private Integer isKeyMaterial;

    private String substituteGroup;

    private Integer substitutePriority;

    private Integer substituteEnabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
