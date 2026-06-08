package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("equipment_event")
public class EquipmentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventNo;
    private String equipmentCode;
    private String lineCode;
    private String eventType;
    private String eventLevel;
    private String lotNo;
    private String stepCode;
    private String recipeCode;
    private String title;
    private String description;
    private String status;
    private String sourceSystem;
    private LocalDateTime occurredTime;
    private String closedBy;
    private LocalDateTime closedTime;
    private String closeConclusion;
    private String reasonCode;
    private String reasonName;
    private String downtimeCategory;
    private String downtimeType;
    private LocalDateTime startedTime;
    private LocalDateTime endedTime;
    private Integer durationMinutes;
    private String impactLevel;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String requestSnapshot;
}
