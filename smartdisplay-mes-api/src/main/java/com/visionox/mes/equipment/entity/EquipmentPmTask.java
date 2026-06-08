package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("equipment_pm_task")
public class EquipmentPmTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;
    private String equipmentCode;
    private String lineCode;
    private String pmType;
    private String pmLevel;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private String status;
    private String checklist;
    private String result;
    private String operator;
    private LocalDateTime completedTime;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String requestSnapshot;
}
