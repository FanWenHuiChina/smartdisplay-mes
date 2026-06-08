package com.visionox.mes.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Recipe详情响应DTO
 */
@Data
@Schema(description = "Recipe详情")
public class RecipeDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Recipe ID")
    private Long id;

    @Schema(description = "Recipe编码")
    private String recipeCode;

    @Schema(description = "Recipe名称")
    private String recipeName;

    @Schema(description = "产品型号")
    private String productCode;

    @Schema(description = "工序编码")
    private String stepCode;

    @Schema(description = "设备编码")
    private String equipmentCode;

    @Schema(description = "Recipe版本号")
    private String recipeVersion;

    @Schema(description = "状态: DRAFT-草稿, ACTIVE-生效, INACTIVE-失效")
    private String status;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "创建人")
    private String createdBy;

    @Schema(description = "创建时间")
    private LocalDateTime createdTime;

    @Schema(description = "更新人")
    private String updatedBy;

    @Schema(description = "更新时间")
    private LocalDateTime updatedTime;

    @Schema(description = "Recipe参数列表")
    private List<RecipeParamDTO> params;
}
