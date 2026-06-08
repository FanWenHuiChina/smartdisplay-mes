package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material_incoming_inspection")
public class MaterialIncomingInspection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String inspectionNo;
    private String batchNo;
    private String materialCode;
    private String materialName;
    private String supplierCode;
    private String result;
    private BigDecimal inspectedQty;
    private BigDecimal sampleQty;
    private String unit;
    private String defectCode;
    private String defectDescription;
    private String coaNo;
    private String conclusion;
    private String inspector;
    private LocalDateTime inspectionTime;
    private String sourceSystem;
    private String requestSnapshot;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
