package com.visionox.mes.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visionox.mes.recipe.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;

/**
 * Recipe Mapper
 */
@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {
}
