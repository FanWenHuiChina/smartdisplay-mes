package com.visionox.mes.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.recipe.dto.RecipeCreateRequest;
import com.visionox.mes.recipe.dto.RecipeDetailVO;
import com.visionox.mes.recipe.dto.RecipeParamDTO;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.entity.RecipeParam;
import com.visionox.mes.recipe.mapper.RecipeMapper;
import com.visionox.mes.recipe.mapper.RecipeParamMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Recipe服务层
 *
 * 业务逻辑：
 * 1. Recipe创建时自动生成参数记录
 * 2. 校验产品+工序+设备+版本的唯一性
 * 3. 支持Recipe版本管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeMapper recipeMapper;
    private final RecipeParamMapper recipeParamMapper;

    /**
     * 创建Recipe
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createRecipe(RecipeCreateRequest request) {
        log.info("创建Recipe: {}", request.getRecipeCode());

        // 1. 校验Recipe编码唯一性
        Long count = recipeMapper.selectCount(
                new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getRecipeCode, request.getRecipeCode())
        );
        if (count > 0) {
            throw new BusinessException("Recipe编码已存在: " + request.getRecipeCode());
        }

        // 2. 校验产品+工序+设备+版本唯一性
        count = recipeMapper.selectCount(
                new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getProductCode, request.getProductCode())
                        .eq(Recipe::getStepCode, request.getStepCode())
                        .eq(Recipe::getEquipmentCode, request.getEquipmentCode())
                        .eq(Recipe::getRecipeVersion, request.getRecipeVersion())
        );
        if (count > 0) {
            throw new BusinessException("该产品+工序+设备+版本的Recipe已存在");
        }

        // 3. 创建Recipe主表
        Recipe recipe = new Recipe();
        BeanUtils.copyProperties(request, recipe);
        recipe.setStatus("DRAFT"); // 默认草稿状态
        recipe.setCreatedBy(AuthContext.username());
        recipeMapper.insert(recipe);

        // 4. 创建Recipe参数
        List<RecipeParam> params = request.getParams().stream()
                .map(dto -> {
                    RecipeParam param = new RecipeParam();
                    BeanUtils.copyProperties(dto, param);
                    param.setRecipeId(recipe.getId());
                    return param;
                })
                .collect(Collectors.toList());

        params.forEach(recipeParamMapper::insert);

        log.info("Recipe创建成功, ID: {}", recipe.getId());
        return recipe.getId();
    }

    /**
     * 查询Recipe详情
     */
    public RecipeDetailVO getRecipeDetail(Long id) {
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BusinessException("Recipe不存在: " + id);
        }

        RecipeDetailVO vo = new RecipeDetailVO();
        BeanUtils.copyProperties(recipe, vo);

        // 查询参数列表
        List<RecipeParam> params = recipeParamMapper.selectList(
                new LambdaQueryWrapper<RecipeParam>()
                        .eq(RecipeParam::getRecipeId, id)
                        .orderByAsc(RecipeParam::getDisplayOrder)
        );

        vo.setParams(params.stream()
                .map(param -> {
                    RecipeParamDTO dto = new RecipeParamDTO();
                    BeanUtils.copyProperties(param, dto);
                    return dto;
                })
                .collect(Collectors.toList()));

        return vo;
    }

    /**
     * 分页查询Recipe列表
     */
    public IPage<Recipe> pageRecipes(Page<Recipe> page, String productCode, String stepCode, String equipmentCode, String status) {
        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();

        if (productCode != null && !productCode.isEmpty()) {
            wrapper.eq(Recipe::getProductCode, productCode);
        }
        if (stepCode != null && !stepCode.isEmpty()) {
            wrapper.eq(Recipe::getStepCode, stepCode);
        }
        if (equipmentCode != null && !equipmentCode.isEmpty()) {
            wrapper.eq(Recipe::getEquipmentCode, equipmentCode);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Recipe::getStatus, status);
        }

        wrapper.orderByDesc(Recipe::getCreatedTime);

        return recipeMapper.selectPage(page, wrapper);
    }

    /**
     * 查找有效Recipe（用于Track In校验）
     */
    public Recipe findActiveRecipe(String productCode, String stepCode, String equipmentCode) {
        log.debug("查找有效Recipe: product={}, step={}, equipment={}", productCode, stepCode, equipmentCode);

        List<Recipe> recipes = recipeMapper.selectList(
                new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getProductCode, productCode)
                        .eq(Recipe::getStepCode, stepCode)
                        .eq(Recipe::getEquipmentCode, equipmentCode)
                        .eq(Recipe::getStatus, "ACTIVE")
                        .orderByDesc(Recipe::getRecipeVersion)
        );

        if (recipes.isEmpty()) {
            throw new BusinessException(
                    String.format("未找到有效Recipe: product=%s, step=%s, equipment=%s",
                            productCode, stepCode, equipmentCode)
            );
        }

        // 返回最新版本的Recipe
        return recipes.get(0);
    }

    /**
     * 激活Recipe
     */
    @Transactional(rollbackFor = Exception.class)
    public void activateRecipe(Long id) {
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BusinessException("Recipe不存在: " + id);
        }

        if ("ACTIVE".equals(recipe.getStatus())) {
            throw new BusinessException("Recipe已经是激活状态");
        }

        recipe.setStatus("ACTIVE");
        recipe.setUpdatedBy(AuthContext.username());
        recipeMapper.updateById(recipe);

        log.info("Recipe已激活: {}", recipe.getRecipeCode());
    }

    /**
     * 停用Recipe
     */
    @Transactional(rollbackFor = Exception.class)
    public void deactivateRecipe(Long id) {
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BusinessException("Recipe不存在: " + id);
        }

        recipe.setStatus("INACTIVE");
        recipe.setUpdatedBy(AuthContext.username());
        recipeMapper.updateById(recipe);

        log.info("Recipe已停用: {}", recipe.getRecipeCode());
    }
}
