package com.visionox.mes.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.ai.entity.AiReportRecord;
import com.visionox.mes.ai.mapper.AiReportRecordMapper;
import com.visionox.mes.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI结果留痕服务。
 */
@Service
@RequiredArgsConstructor
public class AiRecordService {

    private final AiReportRecordMapper aiReportRecordMapper;
    private final ObjectMapper objectMapper;

    public List<Map<String, Object>> records(String reportType,
                                             String bizNo,
                                             String evidenceLevel,
                                             Boolean insufficientEvidence) {
        LambdaQueryWrapper<AiReportRecord> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(reportType)) {
            wrapper.eq(AiReportRecord::getReportType, reportType.trim().toUpperCase(Locale.ROOT));
        }
        if (notBlank(bizNo)) {
            wrapper.eq(AiReportRecord::getBizNo, bizNo.trim());
        }
        if (notBlank(evidenceLevel)) {
            wrapper.eq(AiReportRecord::getEvidenceLevel, evidenceLevel.trim().toUpperCase(Locale.ROOT));
        }
        if (insufficientEvidence != null) {
            wrapper.eq(AiReportRecord::getInsufficientEvidence, insufficientEvidence ? 1 : 0);
        }
        wrapper.orderByDesc(AiReportRecord::getCreatedTime).last("LIMIT 80");
        return aiReportRecordMapper.selectList(wrapper).stream()
                .map(record -> row(record, false))
                .collect(Collectors.toList());
    }

    public Map<String, Object> detail(String reportNo) {
        if (!notBlank(reportNo)) {
            throw new BusinessException("AI报告编号不能为空");
        }
        AiReportRecord record = aiReportRecordMapper.selectOne(new LambdaQueryWrapper<AiReportRecord>()
                .eq(AiReportRecord::getReportNo, reportNo.trim()));
        if (record == null) {
            throw new BusinessException("AI报告不存在: " + reportNo);
        }
        return row(record, true);
    }

    public void record(String reportNo,
                       String reportType,
                       String bizNo,
                       String bizType,
                       String promptTemplateVersion,
                       String modelName,
                       Object inputSnapshot,
                       Object outputJson,
                       String createdBy) {
        record(reportNo, reportType, bizNo, bizType, promptTemplateVersion, modelName,
                inputSnapshot, outputJson, createdBy, Map.of());
    }

    public void record(String reportNo,
                       String reportType,
                       String bizNo,
                       String bizType,
                       String promptTemplateVersion,
                       String modelName,
                       Object inputSnapshot,
                       Object outputJson,
                       String createdBy,
                       Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        AiReportRecord record = new AiReportRecord();
        record.setReportNo(reportNo);
        record.setReportType(reportType);
        record.setBizNo(bizNo);
        record.setBizType(bizType);
        record.setPromptTemplateVersion(promptTemplateVersion);
        record.setModelName(modelName);
        record.setModelProvider(text(safeMetadata, "modelProvider", text(safeMetadata, "provider", null)));
        record.setModelMode(text(safeMetadata, "modelMode", null));
        record.setModelConfigCode(text(safeMetadata, "modelConfigCode", text(safeMetadata, "configCode", null)));
        record.setRetrievalStrategy(text(safeMetadata, "retrievalStrategy", null));
        record.setEvidenceCount(intValue(safeMetadata.get("evidenceCount"), 0));
        record.setMaxEvidenceScore(doubleValue(safeMetadata.get("maxEvidenceScore"), 0.0));
        record.setEvidenceLevel(text(safeMetadata, "evidenceLevel", "NONE"));
        record.setInsufficientEvidence(booleanFlag(safeMetadata.get("insufficientEvidence")));
        record.setModelConfigSnapshot(safeMetadata.containsKey("modelConfigSnapshot")
                ? toJson(safeMetadata.get("modelConfigSnapshot"))
                : null);
        record.setInputSnapshot(toJson(inputSnapshot));
        record.setOutputJson(toJson(outputJson));
        record.setStatus("SUCCESS");
        record.setCreatedBy(createdBy);
        record.setCreatedTime(LocalDateTime.now());
        aiReportRecordMapper.insert(record);
    }

    private Map<String, Object> row(AiReportRecord record, boolean detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reportNo", record.getReportNo());
        row.put("report", record.getReportNo());
        row.put("reportType", record.getReportType());
        row.put("bizNo", record.getBizNo());
        row.put("bizType", record.getBizType());
        row.put("scope", valueOr(record.getBizNo(), valueOr(record.getBizType(), "-")));
        row.put("promptTemplateVersion", record.getPromptTemplateVersion());
        row.put("modelName", record.getModelName());
        row.put("modelProvider", record.getModelProvider());
        row.put("modelMode", record.getModelMode());
        row.put("modelConfigCode", record.getModelConfigCode());
        row.put("retrievalStrategy", record.getRetrievalStrategy());
        row.put("evidenceCount", record.getEvidenceCount() == null ? 0 : record.getEvidenceCount());
        row.put("maxEvidenceScore", record.getMaxEvidenceScore() == null ? 0.0 : record.getMaxEvidenceScore());
        row.put("evidenceLevel", valueOr(record.getEvidenceLevel(), "NONE"));
        row.put("insufficientEvidence", record.getInsufficientEvidence() != null && record.getInsufficientEvidence() == 1);
        row.put("status", statusText(record));
        row.put("recordStatus", record.getStatus());
        row.put("type", rowType(record));
        row.put("createdBy", valueOr(record.getCreatedBy(), "system"));
        row.put("owner", valueOr(record.getCreatedBy(), "system"));
        row.put("createdTime", record.getCreatedTime());
        row.put("time", record.getCreatedTime() == null ? "-" : record.getCreatedTime().toLocalTime().withNano(0).toString());
        if (detail) {
            row.put("inputSnapshot", parseJson(record.getInputSnapshot()));
            row.put("outputJson", parseJson(record.getOutputJson()));
            row.put("modelConfigSnapshot", parseJson(record.getModelConfigSnapshot()));
        }
        return row;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("AI留痕序列化失败", e);
        }
    }

    private Object parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    private String statusText(AiReportRecord record) {
        if (record.getInsufficientEvidence() != null && record.getInsufficientEvidence() == 1) {
            return "依据不足";
        }
        String level = valueOr(record.getEvidenceLevel(), "");
        if (!level.isBlank() && !"NONE".equals(level)) {
            return level;
        }
        return valueOr(record.getModelMode(), valueOr(record.getStatus(), "SUCCESS"));
    }

    private String rowType(AiReportRecord record) {
        if (record.getInsufficientEvidence() != null && record.getInsufficientEvidence() == 1) {
            return "red";
        }
        return switch (valueOr(record.getEvidenceLevel(), "NONE")) {
            case "HIGH" -> "green";
            case "MEDIUM" -> "blue";
            case "LOW" -> "amber";
            case "NONE" -> "green";
            default -> "gray";
        };
    }

    private String text(Map<String, Object> data, String key, String defaultValue) {
        Object value = data.get(key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double doubleValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int booleanFlag(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value instanceof Number number) {
            return number.intValue() == 0 ? 0 : 1;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return ("true".equals(text) || "1".equals(text) || "yes".equals(text)) ? 1 : 0;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
