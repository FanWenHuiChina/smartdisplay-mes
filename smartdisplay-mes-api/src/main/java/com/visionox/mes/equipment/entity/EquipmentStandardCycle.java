package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("equipment_standard_cycle")
public class EquipmentStandardCycle implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String cycleNo;
    private String productCode;
    private String stepCode;
    private String equipmentCode;
    private String recipeCode;
    private String cycleVersion;
    private BigDecimal standardCycleSeconds;
    private BigDecimal lowerCycleSeconds;
    private BigDecimal upperCycleSeconds;
    private String status;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String requestSnapshot;
}
