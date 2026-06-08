package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("equipment_parameter_sample")
public class EquipmentParameterSample implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sampleNo;
    private String equipmentCode;
    private String lineCode;
    private String lotNo;
    private String stepCode;
    private String recipeCode;
    private String paramCode;
    private String paramName;
    private BigDecimal paramValue;
    private String unit;
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;
    private String result;
    private LocalDateTime sampleTime;
    private String sourceSystem;
    private String rawPayload;
    private LocalDateTime createdTime;
}
