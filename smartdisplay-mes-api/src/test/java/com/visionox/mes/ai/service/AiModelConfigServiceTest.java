package com.visionox.mes.ai.service;

import com.visionox.mes.ai.entity.AiModelConfig;
import com.visionox.mes.ai.mapper.AiModelConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelConfigServiceTest {

    @Mock
    private AiModelConfigMapper aiModelConfigMapper;

    @Test
    void resolveForUseCaseShouldReturnActiveConfigSnapshot() {
        AiModelConfig config = new AiModelConfig();
        config.setConfigCode("MOCK_RAG_KEYWORD");
        config.setConfigName("试点SOP问答关键词检索配置");
        config.setUseCase("SOP_QA");
        config.setProvider("LOCAL");
        config.setModelName("mock-rag-output");
        config.setModelMode("SIMULATED");
        config.setPromptTemplateVersion("rag-sop-qa-v2");
        config.setRetrievalStrategy("KEYWORD_FALLBACK");
        config.setTemperature(BigDecimal.ZERO);
        config.setMaxTokens(1200);
        config.setTimeoutMs(30000);
        config.setEnabled(1);
        config.setStatus("ACTIVE");
        config.setModelConfigSnapshot("{\"retrieval\":\"keyword fallback\"}");
        when(aiModelConfigMapper.selectOne(any())).thenReturn(config);

        AiModelConfigService service = new AiModelConfigService(aiModelConfigMapper);
        Map<String, Object> row = service.resolveForUseCase("SOP_QA", "rag-v1", "fallback-model");

        assertThat(row.get("configCode")).isEqualTo("MOCK_RAG_KEYWORD");
        assertThat(row.get("modelProvider")).isEqualTo("LOCAL");
        assertThat(row.get("modelMode")).isEqualTo("SIMULATED");
        assertThat(row.get("retrievalStrategy")).isEqualTo("KEYWORD_FALLBACK");
        assertThat(row.get("type")).isEqualTo("green");
    }

    @Test
    void configsShouldFallbackWhenMapperFails() {
        when(aiModelConfigMapper.selectList(any())).thenThrow(new IllegalStateException("table missing"));

        AiModelConfigService service = new AiModelConfigService(aiModelConfigMapper);
        List<Map<String, Object>> rows = service.configs();

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(row -> row.get("configCode"))
                .contains("FALLBACK_YIELD_DAILY", "FALLBACK_EQUIPMENT_ANALYSIS", "FALLBACK_SOP_QA");
    }
}
