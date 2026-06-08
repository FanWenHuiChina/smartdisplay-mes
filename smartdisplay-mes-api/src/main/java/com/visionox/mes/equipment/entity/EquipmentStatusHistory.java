package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("equipment_status_history")
public class EquipmentStatusHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String historyNo;
    private String equipmentCode;
    private String lineCode;
    private String fromStatus;
    private String toStatus;
    private String changeReason;
    private String sourceSystem;
    private String changedBy;
    private LocalDateTime changedTime;
    private LocalDateTime createdTime;
    private String requestSnapshot;
}
