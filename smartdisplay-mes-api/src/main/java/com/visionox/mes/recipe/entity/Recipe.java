package com.visionox.mes.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Recipe配方主表实体
 *
 * 业务说明：
 * - Recipe是设备加工参数包，包含温度、压力、时间等目标值和上下限
 * - OLED生产对参数控制要求极高，错误Recipe直接影响良率
 * - Track In时强制校验Recipe（产品+工序+设备必须匹配）
 */
@Data
@TableName("md_recipe")
public class Recipe implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Recipe编码（唯一）
     */
    private String recipeCode;

    /**
     * Recipe名称
     */
    private String recipeName;

    /**
     * 产品型号
     */
    private String productCode;

    /**
     * 工序编码
     */
    private String stepCode;

    /**
     * 设备编码
     */
    private String equipmentCode;

    /**
     * Recipe版本号
     */
    private String recipeVersion;

    /**
     * 状态: DRAFT-草稿, ACTIVE-生效, INACTIVE-失效
     */
    private String status;

    /**
     * 描述
     */
    private String description;

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
     * 更新人
     */
    private String updatedBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;

    /**
     * 逻辑删除: 0-未删除, 1-已删除
     */
    @TableLogic
    private Integer deleted;
}
