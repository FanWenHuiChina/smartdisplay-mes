package com.visionox.mes.recipe.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Recipe参数表实体
 *
 * 业务说明：
 * - 记录Recipe的具体参数：温度、压力、时间、速度等
 * - 包含目标值、上限、下限，用于Track Out时参数校验
 * - 关键参数超限会触发Hold
 */
@Data
@TableName("md_recipe_param")
public class RecipeParam implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Recipe主表ID
     */
    private Long recipeId;

    /**
     * 参数名称
     */
    private String paramName;

    /**
     * 参数编码
     */
    private String paramCode;

    /**
     * 目标值
     */
    private BigDecimal targetValue;

    /**
     * 上限
     */
    private BigDecimal upperLimit;

    /**
     * 下限
     */
    private BigDecimal lowerLimit;

    /**
     * 单位
     */
    private String unit;

    /**
     * 参数类型: TEMPERATURE-温度, PRESSURE-压力, TIME-时间, SPEED-速度
     */
    private String paramType;

    /**
     * 是否关键参数: 0-否, 1-是
     */
    private Integer isKeyParam;

    /**
     * 显示顺序
     */
    private Integer displayOrder;

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
