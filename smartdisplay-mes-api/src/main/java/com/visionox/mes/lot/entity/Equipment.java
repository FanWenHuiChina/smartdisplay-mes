package com.visionox.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备实体
 */
@Data
@TableName("md_equipment")
public class Equipment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 设备编码（唯一）
     */
    private String equipmentCode;

    /**
     * 设备名称
     */
    private String equipmentName;

    /**
     * 设备类型
     */
    private String equipmentType;

    /**
     * 产线编码
     */
    private String lineCode;

    /**
     * 状态: IDLE-空闲, RUNNING-运行中, ALARM-报警, DOWN-宕机, PM-保养, OFFLINE-离线
     */
    private String status;

    /**
     * 支持的工序列表(JSON数组)
     */
    private String capabilitySteps;

    /**
     * 位置
     */
    private String location;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
