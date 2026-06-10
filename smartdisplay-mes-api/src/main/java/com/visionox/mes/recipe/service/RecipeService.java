package com.visionox.mes.recipe.service;

import cn.hutool.json.JSONUtil;
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
import com.visionox.mes.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final AuditLogService auditLogService;

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
        audit("RECIPE_CREATE", recipe.getRecipeCode(), "创建Recipe草稿",
                auditSnapshot(null, recipeSnapshot(recipe), createRequestSnapshot(request, params.size())));

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
        activateRecipe(id, "RECIPE_ACTIVATE", "激活Recipe");
    }

    /**
     * 发布Recipe版本。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publishRecipe(Long id) {
        activateRecipe(id, "RECIPE_PUBLISH", "发布Recipe版本");
    }

    private void activateRecipe(Long id, String auditAction, String actionLabel) {
        Recipe recipe = recipeMapper.selectById(id);
        if (recipe == null) {
            throw new BusinessException("Recipe不存在: " + id);
        }

        if ("ACTIVE".equals(recipe.getStatus())) {
            throw new BusinessException("Recipe已经是激活状态");
        }

        Map<String, Object> before = recipeSnapshot(recipe);
        List<RecipeStatusChange> replacedChanges = deactivateReplacedActiveRecipes(recipe);

        recipe.setStatus("ACTIVE");
        recipe.setUpdatedBy(AuthContext.username());
        recipeMapper.updateById(recipe);

        replacedChanges.forEach(change -> audit("RECIPE_AUTO_DEACTIVATE",
                (String) change.after().get("recipeCode"),
                "自动停用同上下文旧版Recipe",
                auditSnapshot(change.before(), change.after(), autoDeactivateRequest(recipe))));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("id", id);
        request.put("singleActiveContext", singleActiveContext(recipe));
        request.put("replacedActiveCount", replacedChanges.size());
        request.put("replacedActiveRecipes", replacedChanges.stream()
                .map(RecipeStatusChange::before)
                .toList());

        audit(auditAction, recipe.getRecipeCode(), actionLabel,
                auditSnapshot(before, recipeSnapshot(recipe), request));

        log.info("Recipe已激活: {}", recipe.getRecipeCode());
    }

    private List<RecipeStatusChange> deactivateReplacedActiveRecipes(Recipe targetRecipe) {
        List<Recipe> activeRecipes = recipeMapper.selectList(
                new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getProductCode, targetRecipe.getProductCode())
                        .eq(Recipe::getStepCode, targetRecipe.getStepCode())
                        .eq(Recipe::getEquipmentCode, targetRecipe.getEquipmentCode())
                        .eq(Recipe::getStatus, "ACTIVE")
                        .ne(Recipe::getId, targetRecipe.getId())
                        .orderByDesc(Recipe::getRecipeVersion)
        );
        if (activeRecipes == null || activeRecipes.isEmpty()) {
            return List.of();
        }

        return activeRecipes.stream()
                .map(activeRecipe -> {
                    Map<String, Object> before = recipeSnapshot(activeRecipe);
                    activeRecipe.setStatus("INACTIVE");
                    activeRecipe.setUpdatedBy(AuthContext.username());
                    recipeMapper.updateById(activeRecipe);
                    return new RecipeStatusChange(before, recipeSnapshot(activeRecipe));
                })
                .toList();
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

        Map<String, Object> before = recipeSnapshot(recipe);
        recipe.setStatus("INACTIVE");
        recipe.setUpdatedBy(AuthContext.username());
        recipeMapper.updateById(recipe);
        audit("RECIPE_DEACTIVATE", recipe.getRecipeCode(), "停用Recipe",
                auditSnapshot(before, recipeSnapshot(recipe), Map.of("id", id)));

        log.info("Recipe已停用: {}", recipe.getRecipeCode());
    }

    private void audit(String action, String bizNo, String description, String requestSnapshot) {
        try {
            auditLogService.record(action, bizNo, "RECIPE", description, AuthContext.username(),
                    "recipe-service", requestSnapshot);
        } catch (Exception e) {
            log.warn("Recipe审计写入失败，已降级不阻断主流程: action={}, bizNo={}, reason={}",
                    action, bizNo, e.getMessage());
        }
    }

    private String auditSnapshot(Map<String, Object> before, Map<String, Object> after, Map<String, Object> request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("before", before == null ? Map.of() : before);
        snapshot.put("after", after == null ? Map.of() : after);
        snapshot.put("changedFields", changedFields(before, after));
        snapshot.put("request", request == null ? Map.of() : request);
        return JSONUtil.toJsonStr(snapshot);
    }

    private List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> safeBefore = before == null ? Map.of() : before;
        Map<String, Object> safeAfter = after == null ? Map.of() : after;
        return safeAfter.keySet().stream()
                .filter(key -> !Objects.equals(safeBefore.get(key), safeAfter.get(key)))
                .sorted()
                .toList();
    }

    private Map<String, Object> recipeSnapshot(Recipe recipe) {
        if (recipe == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", recipe.getId());
        snapshot.put("recipeCode", recipe.getRecipeCode());
        snapshot.put("recipeName", recipe.getRecipeName());
        snapshot.put("productCode", recipe.getProductCode());
        snapshot.put("stepCode", recipe.getStepCode());
        snapshot.put("equipmentCode", recipe.getEquipmentCode());
        snapshot.put("recipeVersion", recipe.getRecipeVersion());
        snapshot.put("status", recipe.getStatus());
        snapshot.put("updatedBy", recipe.getUpdatedBy());
        return snapshot;
    }

    private Map<String, Object> createRequestSnapshot(RecipeCreateRequest request, int paramCount) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recipeCode", request.getRecipeCode());
        snapshot.put("productCode", request.getProductCode());
        snapshot.put("stepCode", request.getStepCode());
        snapshot.put("equipmentCode", request.getEquipmentCode());
        snapshot.put("recipeVersion", request.getRecipeVersion());
        snapshot.put("paramCount", paramCount);
        return snapshot;
    }

    private Map<String, Object> singleActiveContext(Recipe recipe) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("productCode", recipe.getProductCode());
        context.put("stepCode", recipe.getStepCode());
        context.put("equipmentCode", recipe.getEquipmentCode());
        return context;
    }

    private Map<String, Object> autoDeactivateRequest(Recipe triggerRecipe) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("triggerRecipeId", triggerRecipe.getId());
        request.put("triggerRecipeCode", triggerRecipe.getRecipeCode());
        request.put("triggerRecipeVersion", triggerRecipe.getRecipeVersion());
        request.put("singleActiveContext", singleActiveContext(triggerRecipe));
        return request;
    }

    private record RecipeStatusChange(Map<String, Object> before, Map<String, Object> after) {
    }
}
