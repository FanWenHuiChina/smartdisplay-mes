package com.visionox.mes.recipe.service;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.recipe.dto.RecipeCreateRequest;
import com.visionox.mes.recipe.dto.RecipeParamDTO;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.entity.RecipeParam;
import com.visionox.mes.recipe.mapper.RecipeMapper;
import com.visionox.mes.recipe.mapper.RecipeParamMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private RecipeParamMapper recipeParamMapper;

    @InjectMocks
    private RecipeService recipeService;

    @Test
    void createRecipeShouldRejectDuplicateRecipeCode() {
        when(recipeMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> recipeService.createRecipe(createRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RCP-COAT-V1");

        verify(recipeMapper, never()).insert(any());
        verify(recipeParamMapper, never()).insert(any());
    }

    @Test
    void createRecipeShouldRejectDuplicateProductStepEquipmentVersion() {
        when(recipeMapper.selectCount(any())).thenReturn(0L, 1L);

        assertThatThrownBy(() -> recipeService.createRecipe(createRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Recipe");

        verify(recipeMapper, never()).insert(any());
        verify(recipeParamMapper, never()).insert(any());
    }

    @Test
    void createRecipeShouldPersistDraftRecipeAndParams() {
        when(recipeMapper.selectCount(any())).thenReturn(0L, 0L);
        doAnswer(invocation -> {
            Recipe recipe = invocation.getArgument(0);
            recipe.setId(100L);
            return 1;
        }).when(recipeMapper).insert(any(Recipe.class));

        Long recipeId = recipeService.createRecipe(createRequest());

        assertThat(recipeId).isEqualTo(100L);

        ArgumentCaptor<Recipe> recipeCaptor = ArgumentCaptor.forClass(Recipe.class);
        verify(recipeMapper).insert(recipeCaptor.capture());
        Recipe recipe = recipeCaptor.getValue();
        assertThat(recipe.getRecipeCode()).isEqualTo("RCP-COAT-V1");
        assertThat(recipe.getProductCode()).isEqualTo("OLED_PANEL");
        assertThat(recipe.getStepCode()).isEqualTo("COATING");
        assertThat(recipe.getEquipmentCode()).isEqualTo("COATER_01");
        assertThat(recipe.getRecipeVersion()).isEqualTo("V1");
        assertThat(recipe.getStatus()).isEqualTo("DRAFT");
        assertThat(recipe.getCreatedBy()).isEqualTo("system");

        ArgumentCaptor<RecipeParam> paramCaptor = ArgumentCaptor.forClass(RecipeParam.class);
        verify(recipeParamMapper).insert(paramCaptor.capture());
        RecipeParam param = paramCaptor.getValue();
        assertThat(param.getRecipeId()).isEqualTo(100L);
        assertThat(param.getParamCode()).isEqualTo("THICKNESS");
        assertThat(param.getTargetValue()).isEqualByComparingTo("65.0");
        assertThat(param.getLowerLimit()).isEqualByComparingTo("60.0");
        assertThat(param.getUpperLimit()).isEqualByComparingTo("70.0");
        assertThat(param.getIsKeyParam()).isEqualTo(1);
    }

    @Test
    void findActiveRecipeShouldRejectWhenNoActiveRecipeMatches() {
        when(recipeMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> recipeService.findActiveRecipe("OLED_PANEL", "COATING", "COATER_01"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OLED_PANEL")
                .hasMessageContaining("COATING")
                .hasMessageContaining("COATER_01");
    }

    @Test
    void findActiveRecipeShouldReturnFirstActiveRecipeFromVersionSortedQuery() {
        Recipe latest = recipe("RCP-COAT-V3", "V3", "ACTIVE");
        Recipe previous = recipe("RCP-COAT-V2", "V2", "ACTIVE");
        when(recipeMapper.selectList(any())).thenReturn(List.of(latest, previous));

        Recipe recipe = recipeService.findActiveRecipe("OLED_PANEL", "COATING", "COATER_01");

        assertThat(recipe.getRecipeCode()).isEqualTo("RCP-COAT-V3");
    }

    @Test
    void activateRecipeShouldRejectMissingRecipe() {
        when(recipeMapper.selectById(100L)).thenReturn(null);

        assertThatThrownBy(() -> recipeService.activateRecipe(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("100");

        verify(recipeMapper, never()).updateById(any());
    }

    @Test
    void activateRecipeShouldRejectAlreadyActiveRecipe() {
        when(recipeMapper.selectById(100L)).thenReturn(recipe("RCP-COAT-V1", "V1", "ACTIVE"));

        assertThatThrownBy(() -> recipeService.activateRecipe(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Recipe");

        verify(recipeMapper, never()).updateById(any());
    }

    @Test
    void activateRecipeShouldSetRecipeActiveAndOperator() {
        Recipe recipe = recipe("RCP-COAT-V1", "V1", "DRAFT");
        when(recipeMapper.selectById(100L)).thenReturn(recipe);

        recipeService.activateRecipe(100L);

        assertThat(recipe.getStatus()).isEqualTo("ACTIVE");
        assertThat(recipe.getUpdatedBy()).isEqualTo("system");
        verify(recipeMapper).updateById(recipe);
    }

    @Test
    void deactivateRecipeShouldSetRecipeInactiveAndOperator() {
        Recipe recipe = recipe("RCP-COAT-V1", "V1", "ACTIVE");
        when(recipeMapper.selectById(100L)).thenReturn(recipe);

        recipeService.deactivateRecipe(100L);

        assertThat(recipe.getStatus()).isEqualTo("INACTIVE");
        assertThat(recipe.getUpdatedBy()).isEqualTo("system");
        verify(recipeMapper).updateById(recipe);
    }

    private RecipeCreateRequest createRequest() {
        RecipeCreateRequest request = new RecipeCreateRequest();
        request.setRecipeCode("RCP-COAT-V1");
        request.setRecipeName("COATING recipe");
        request.setProductCode("OLED_PANEL");
        request.setStepCode("COATING");
        request.setEquipmentCode("COATER_01");
        request.setRecipeVersion("V1");
        request.setDescription("Pilot recipe");
        request.setParams(List.of(param()));
        return request;
    }

    private RecipeParamDTO param() {
        RecipeParamDTO param = new RecipeParamDTO();
        param.setParamCode("THICKNESS");
        param.setParamName("Film thickness");
        param.setTargetValue(new BigDecimal("65.0"));
        param.setLowerLimit(new BigDecimal("60.0"));
        param.setUpperLimit(new BigDecimal("70.0"));
        param.setUnit("nm");
        param.setParamType("THICKNESS");
        param.setIsKeyParam(1);
        param.setDisplayOrder(1);
        return param;
    }

    private Recipe recipe(String recipeCode, String version, String status) {
        Recipe recipe = new Recipe();
        recipe.setId(100L);
        recipe.setRecipeCode(recipeCode);
        recipe.setRecipeName(recipeCode);
        recipe.setProductCode("OLED_PANEL");
        recipe.setStepCode("COATING");
        recipe.setEquipmentCode("COATER_01");
        recipe.setRecipeVersion(version);
        recipe.setStatus(status);
        return recipe;
    }
}
