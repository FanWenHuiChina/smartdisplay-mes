package com.visionox.mes.recipe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.List;

/**
 * 创建Recipe请求DTO
 */
@Data
@Schema(description = "创建Recipe请求")
public class RecipeCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Recipe编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Recipe编码不能为空")
    private String recipeCode;

    @Schema(description = "Recipe名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Recipe名称不能为空")
    private String recipeName;

    @Schema(description = "产品型号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "产品型号不能为空")
    private String productCode;

    @Schema(description = "工序编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "工序编码不能为空")
    private String stepCode;

    @Schema(description = "设备编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备编码不能为空")
    private String equipmentCode;

    @Schema(description = "Recipe版本号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Recipe版本号不能为空")
    private String recipeVersion;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "Recipe参数列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Recipe参数列表不能为空")
    private List<RecipeParamDTO> params;
}
