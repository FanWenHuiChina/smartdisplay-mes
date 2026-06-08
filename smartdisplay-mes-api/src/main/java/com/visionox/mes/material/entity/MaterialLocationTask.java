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
 * WMS库位作业任务。
 */
@Data
@TableName("material_location_task")
public class MaterialLocationTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;
    private String taskType;
    private String batchNo;
    private String materialCode;
    private String materialName;
    private String sourceLocation;
    private String targetLocation;
    private BigDecimal plannedQty;
    private BigDecimal actualQty;
    private String unit;
    private String status;
    private String reason;
    private String operator;
    private LocalDateTime executedTime;
    private String assignedTo;
    private LocalDateTime assignedTime;
    private String reviewer;
    private LocalDateTime reviewedTime;
    private String cancelledBy;
    private LocalDateTime cancelledTime;
    private String cancelReason;
    private String exceptionReason;
    private String taskSource;
    private LocalDateTime completedTime;
    private String requestSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
