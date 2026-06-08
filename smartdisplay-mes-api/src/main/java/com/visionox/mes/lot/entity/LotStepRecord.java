package com.visionox.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Lot过站记录实体
 */
@Data
@TableName("prod_lot_step_record")
public class LotStepRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Lot批次号
     */
    private String lotNo;

    /**
     * 工序编码
     */
    private String stepCode;

    /**
     * 设备编码
     */
    private String equipmentCode;

    /**
     * Recipe编码
     */
    private String recipeCode;

    /**
     * 进站时间
     */
    private LocalDateTime trackInTime;

    /**
     * 出站时间
     */
    private LocalDateTime trackOutTime;

    /**
     * 操作员
     */
    private String operator;

    /**
     * 加工参数(JSON)
     */
    private String processParams;

    /**
     * 结果: OK-合格, NG-不合格
     */
    private String result;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
