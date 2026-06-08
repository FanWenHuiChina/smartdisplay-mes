package com.visionox.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Hold记录实体
 */
@Data
@TableName("lot_hold_record")
public class HoldRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Lot批次号
     */
    private String lotNo;

    /**
     * Hold原因
     */
    private String holdReason;

    /**
     * Hold类型: QUALITY-质量异常, EQUIPMENT-设备故障, MATERIAL-物料问题, ENGINEERING-工程变更
     */
    private String holdType;

    /**
     * Hold操作人
     */
    private String holdBy;

    /**
     * Hold时间
     */
    private LocalDateTime holdTime;

    /**
     * Release操作人
     */
    private String releaseBy;

    /**
     * Release时间
     */
    private LocalDateTime releaseTime;

    /**
     * 处置结果
     */
    private String disposition;

    /**
     * 状态: HOLD-已Hold, RELEASED-已Release
     */
    private String status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
