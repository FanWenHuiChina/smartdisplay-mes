package com.visionox.mes.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.ai.entity.AiModelConfig;
import com.visionox.mes.ai.mapper.AiModelConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI模型运行配置服务。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiModelConfigService {

    private final AiModelConfigMapper aiModelConfigMapper;

    public List<Map<String, Object>> configs() {
        try {
            List<AiModelConfig> rows = aiModelConfigMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                    .orderByAsc(AiModelConfig::getUseCase)
                    .orderByDesc(AiModelConfig::getEnabled)
                    .orderByAsc(AiModelConfig::getConfigCode));
            if (rows != null && !rows.isEmpty()) {
                return rows.stream().map(this::row).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("AI模型配置读取失败，已返回试点fallback配置: {}", e.getMessage());
        }
        return fallbackConfigs();
    }

    public Map<String, Object> resolveForUseCase(String useCase,
                                                 String defaultPromptTemplateVersion,
                                                 String defaultModelName) {
        String normalizedUseCase = normalizeUseCase(useCase);
        try {
            AiModelConfig config = aiModelConfigMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                    .eq(AiModelConfig::getUseCase, normalizedUseCase)
                    .eq(AiModelConfig::getStatus, "ACTIVE")
                    .eq(AiModelConfig::getEnabled, 1)
                    .orderByDesc(AiModelConfig::getUpdatedTime)
                    .orderByDesc(AiModelConfig::getId)
                    .last("LIMIT 1"));
            if (config != null) {
                return row(config);
            }
        } catch (Exception e) {
            log.warn("AI模型配置解析失败，已降级到{}默认配置: {}", normalizedUseCase, e.getMessage());
        }
        return fallbackConfig(normalizedUseCase, defaultPromptTemplateVersion, defaultModelName);
    }

    private List<Map<String, Object>> fallbackConfigs() {
        return List.of(
                fallbackConfig("YIELD_DAILY", "yield-daily-v2", "mock-structured-output"),
                fallbackConfig("EQUIPMENT_ANALYSIS", "equipment-analyze-v2", "mock-structured-output"),
                fallbackConfig("SOP_QA", "rag-sop-qa-v2", "mock-rag-output")
        );
    }

    private Map<String, Object> fallbackConfig(String useCase, String promptTemplateVersion, String modelName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("configCode", "FALLBACK_" + normalizeUseCase(useCase));
        row.put("configName", "试点fallback AI配置");
        row.put("useCase", normalizeUseCase(useCase));
        row.put("provider", "LOCAL");
        row.put("modelProvider", "LOCAL");
        row.put("modelName", valueOr(modelName, "mock-structured-output"));
        row.put("modelMode", "SIMULATED");
        row.put("endpointUri", "");
        row.put("promptTemplateVersion", valueOr(promptTemplateVersion, "pilot-v1"));
        row.put("retrievalStrategy", retrievalStrategyFor(useCase));
        row.put("temperature", BigDecimal.ZERO);
        row.put("maxTokens", 1200);
        row.put("timeoutMs", 30000);
        row.put("enabled", 1);
        row.put("status", "ACTIVE");
        row.put("modelConfigSnapshot", "{\"boundary\":\"fallback local deterministic output\",\"writeActionAllowed\":false}");
        row.put("type", "amber");
        return row;
    }

    private Map<String, Object> row(AiModelConfig config) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("configCode", config.getConfigCode());
        row.put("configName", config.getConfigName());
        row.put("useCase", config.getUseCase());
        row.put("provider", config.getProvider());
        row.put("modelProvider", config.getProvider());
        row.put("modelName", config.getModelName());
        row.put("modelMode", config.getModelMode());
        row.put("endpointUri", config.getEndpointUri());
        row.put("promptTemplateVersion", config.getPromptTemplateVersion());
        row.put("retrievalStrategy", config.getRetrievalStrategy());
        row.put("temperature", config.getTemperature());
        row.put("maxTokens", config.getMaxTokens());
        row.put("timeoutMs", config.getTimeoutMs());
        row.put("enabled", config.getEnabled());
        row.put("status", config.getStatus());
        row.put("modelConfigSnapshot", config.getModelConfigSnapshot());
        row.put("updatedBy", config.getUpdatedBy());
        row.put("updatedTime", config.getUpdatedTime());
        row.put("type", statusType(config.getStatus(), config.getEnabled()));
        return row;
    }

    private String retrievalStrategyFor(String useCase) {
        return switch (normalizeUseCase(useCase)) {
            case "SOP_QA" -> "KEYWORD_FALLBACK";
            case "EQUIPMENT_ANALYSIS" -> "MES_AND_RAG";
            case "YIELD_DAILY" -> "MES_SNAPSHOT";
            default -> "NONE";
        };
    }

    private String statusType(String status, Integer enabled) {
        if (enabled == null || enabled == 0) {
            return "gray";
        }
        return "ACTIVE".equalsIgnoreCase(valueOr(status, "")) ? "green" : "amber";
    }

    private String normalizeUseCase(String useCase) {
        return valueOr(useCase, "GENERAL").trim().toUpperCase(Locale.ROOT);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
