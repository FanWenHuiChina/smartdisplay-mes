package com.visionox.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Lot批次实体
 *
 * 业务说明：
 * - Lot是MES运行时核心对象，在工艺路线中流转
 * - Track In/Out时更新current_step和current_equipment
 * - Hold状态的Lot不能继续Track In
 */
@Data
@TableName("prod_lot")
public class Lot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Lot批次号（唯一）
     */
    private String lotNo;

    /**
     * 工单号
     */
    private String orderNo;

    /**
     * 产品型号
     */
    private String productCode;

    /**
     * 所属产线，用于执行域数据范围控制
     */
    private String lineCode;

    /**
     * 数量
     */
    private Integer qty;

    /**
     * 当前工序编码
     */
    private String currentStepCode;

    /**
     * 当前设备编码
     */
    private String currentEquipmentCode;

    /**
     * 状态: CREATED-已创建, READY-就绪, PROCESSING-加工中, HOLD-暂停, COMPLETED-完成, SCRAPPED-报废
     */
    private String status;

    /**
     * Hold标记: 0-正常, 1-已Hold
     */
    private Integer holdFlag;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 创建人
     */
    private String createdBy;

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
