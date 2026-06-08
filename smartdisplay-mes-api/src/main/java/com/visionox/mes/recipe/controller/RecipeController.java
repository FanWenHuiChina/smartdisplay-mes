package com.visionox.mes.recipe.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visionox.mes.common.Result;
import com.visionox.mes.recipe.dto.RecipeCreateRequest;
import com.visionox.mes.recipe.dto.RecipeDetailVO;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Recipe管理接口
 *
 * 业务说明：
 * - Recipe是OLED生产的核心，包含设备加工的所有参数
 * - Track In时必须校验Recipe存在且有效
 * - 支持Recipe版本管理，确保追溯准确性
 */
@Tag(name = "Recipe管理", description = "Recipe配方管理接口")
@RestController
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final RecipeService recipeService;

    /**
     * 创建Recipe
     */
    @Operation(summary = "创建Recipe", description = "创建新的Recipe配方，包含参数列表")
    @PostMapping
    public Result<Long> createRecipe(@Validated @RequestBody RecipeCreateRequest request) {
        Long id = recipeService.createRecipe(request);
        return Result.success("Recipe创建成功", id);
    }

    /**
     * 查询Recipe详情
     */
    @Operation(summary = "查询Recipe详情", description = "根据ID查询Recipe详情，包含所有参数")
    @GetMapping("/{id}")
    public Result<RecipeDetailVO> getRecipeDetail(
            @Parameter(description = "Recipe ID") @PathVariable Long id) {
        RecipeDetailVO detail = recipeService.getRecipeDetail(id);
        return Result.success(detail);
    }

    /**
     * 分页查询Recipe列表
     */
    @Operation(summary = "分页查询Recipe列表", description = "支持按产品、工序、设备、状态筛选")
    @GetMapping
    public Result<IPage<Recipe>> pageRecipes(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "产品型号") @RequestParam(required = false) String productCode,
            @Parameter(description = "工序编码") @RequestParam(required = false) String stepCode,
            @Parameter(description = "设备编码") @RequestParam(required = false) String equipmentCode,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {

        Page<Recipe> page = new Page<>(current, size);
        IPage<Recipe> result = recipeService.pageRecipes(page, productCode, stepCode, equipmentCode, status);
        return Result.success(result);
    }

    /**
     * 查找有效Recipe（用于Track In校验）
     */
    @Operation(summary = "查找有效Recipe", description = "根据产品+工序+设备查找生效状态的Recipe")
    @GetMapping("/search")
    public Result<Recipe> findActiveRecipe(
            @Parameter(description = "产品型号", required = true) @RequestParam String productCode,
            @Parameter(description = "工序编码", required = true) @RequestParam String stepCode,
            @Parameter(description = "设备编码", required = true) @RequestParam String equipmentCode) {

        Recipe recipe = recipeService.findActiveRecipe(productCode, stepCode, equipmentCode);
        return Result.success(recipe);
    }

    /**
     * 激活Recipe
     */
    @Operation(summary = "激活Recipe", description = "将Recipe状态设置为ACTIVE")
    @PutMapping("/{id}/activate")
    public Result<Void> activateRecipe(
            @Parameter(description = "Recipe ID") @PathVariable Long id) {
        recipeService.activateRecipe(id);
        return Result.success();
    }

    /**
     * 停用Recipe
     */
    @Operation(summary = "停用Recipe", description = "将Recipe状态设置为INACTIVE")
    @PutMapping("/{id}/deactivate")
    public Result<Void> deactivateRecipe(
            @Parameter(description = "Recipe ID") @PathVariable Long id) {
        recipeService.deactivateRecipe(id);
        return Result.success();
    }
}
