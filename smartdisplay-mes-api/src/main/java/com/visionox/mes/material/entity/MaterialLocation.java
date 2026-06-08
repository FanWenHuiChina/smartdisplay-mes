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
 * WMS库位主数据。
 */
@Data
@TableName("md_material_location")
public class MaterialLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String locationCode;
    private String zoneCode;
    private String areaCode;
    private String storageType;
    private String materialClass;
    private String status;
    private BigDecimal capacityQty;
    private BigDecimal usedQty;
    private String unit;
    private BigDecimal temperatureMin;
    private BigDecimal temperatureMax;
    private BigDecimal humidityMin;
    private BigDecimal humidityMax;
    private Integer strategyPriority;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
