package com.visionox.mes.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visionox.mes.ai.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI模型运行配置 Mapper。
 */
@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {
}
