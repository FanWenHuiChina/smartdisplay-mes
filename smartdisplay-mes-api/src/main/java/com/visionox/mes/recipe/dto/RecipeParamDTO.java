package com.visionox.mes.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Recipe参数DTO
 */
@Data
@Schema(description = "Recipe参数")
public class RecipeParamDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "参数ID")
    private Long id;

    @Schema(description = "参数名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "参数名称不能为空")
    private String paramName;

    @Schema(description = "参数编码")
    private String paramCode;

    @Schema(description = "目标值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "目标值不能为空")
    private BigDecimal targetValue;

    @Schema(description = "上限", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "上限不能为空")
    private BigDecimal upperLimit;

    @Schema(description = "下限", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "下限不能为空")
    private BigDecimal lowerLimit;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "参数类型: TEMPERATURE-温度, PRESSURE-压力, TIME-时间, SPEED-速度")
    private String paramType;

    @Schema(description = "是否关键参数: 0-否, 1-是")
    private Integer isKeyParam;

    @Schema(description = "显示顺序")
    private Integer displayOrder;

    @Schema(description = "备注")
    private String remark;
}
