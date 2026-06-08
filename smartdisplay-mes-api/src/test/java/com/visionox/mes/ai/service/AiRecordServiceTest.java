package com.visionox.mes.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionox.mes.ai.entity.AiReportRecord;
import com.visionox.mes.ai.mapper.AiReportRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRecordServiceTest {

    @Mock
    private AiReportRecordMapper aiReportRecordMapper;

    @Test
    void recordShouldPersistModelAndEvidenceMetadata() {
        AiRecordService service = new AiRecordService(aiReportRecordMapper, new ObjectMapper());

        service.record(
                "AIR-KB-001",
                "SOP_QA",
                "涂胶膜厚超限",
                "SOP_KB",
                "rag-sop-qa-v2",
                "mock-rag-output",
                Map.of("question", "涂胶膜厚超限"),
                Map.of("answer", "依据SOP处置"),
                "qe",
                Map.of(
                        "modelProvider", "LOCAL",
                        "modelMode", "SIMULATED",
                        "modelConfigCode", "MOCK_RAG_KEYWORD",
                        "retrievalStrategy", "KEYWORD_FALLBACK",
                        "evidenceCount", 2,
                        "maxEvidenceScore", 0.91,
                        "evidenceLevel", "HIGH",
                        "insufficientEvidence", false,
                        "modelConfigSnapshot", Map.of("configCode", "MOCK_RAG_KEYWORD")
                )
        );

        ArgumentCaptor<AiReportRecord> captor = ArgumentCaptor.forClass(AiReportRecord.class);
        verify(aiReportRecordMapper).insert(captor.capture());
        AiReportRecord record = captor.getValue();

        assertThat(record.getReportNo()).isEqualTo("AIR-KB-001");
        assertThat(record.getModelProvider()).isEqualTo("LOCAL");
        assertThat(record.getModelMode()).isEqualTo("SIMULATED");
        assertThat(record.getModelConfigCode()).isEqualTo("MOCK_RAG_KEYWORD");
        assertThat(record.getRetrievalStrategy()).isEqualTo("KEYWORD_FALLBACK");
        assertThat(record.getEvidenceCount()).isEqualTo(2);
        assertThat(record.getMaxEvidenceScore()).isEqualTo(0.91);
        assertThat(record.getEvidenceLevel()).isEqualTo("HIGH");
        assertThat(record.getInsufficientEvidence()).isZero();
        assertThat(record.getModelConfigSnapshot()).contains("MOCK_RAG_KEYWORD");
    }

    @Test
    void recordsShouldReturnAuditSummariesWithoutLargeSnapshots() {
        AiReportRecord record = reportRecord();
        when(aiReportRecordMapper.selectList(any())).thenReturn(List.of(record));
        AiRecordService service = new AiRecordService(aiReportRecordMapper, new ObjectMapper());

        List<Map<String, Object>> rows = service.records("sop_qa", "涂胶膜厚超限", "high", false);

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("reportNo")).isEqualTo("AIR-KB-001");
        assertThat(row.get("scope")).isEqualTo("涂胶膜厚超限");
        assertThat(row.get("modelMode")).isEqualTo("SIMULATED");
        assertThat(row.get("status")).isEqualTo("HIGH");
        assertThat(row.get("type")).isEqualTo("green");
        assertThat(row).doesNotContainKeys("inputSnapshot", "outputJson", "modelConfigSnapshot");
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailShouldReturnParsedSnapshots() {
        AiReportRecord record = reportRecord();
        when(aiReportRecordMapper.selectOne(any())).thenReturn(record);
        AiRecordService service = new AiRecordService(aiReportRecordMapper, new ObjectMapper());

        Map<String, Object> detail = service.detail("AIR-KB-001");

        assertThat(detail.get("reportNo")).isEqualTo("AIR-KB-001");
        assertThat((Map<String, Object>) detail.get("inputSnapshot")).containsEntry("question", "涂胶膜厚超限");
        assertThat((Map<String, Object>) detail.get("outputJson")).containsEntry("answer", "依据SOP处置");
        assertThat((Map<String, Object>) detail.get("modelConfigSnapshot")).containsEntry("configCode", "MOCK_RAG_KEYWORD");
    }

    private AiReportRecord reportRecord() {
        AiReportRecord record = new AiReportRecord();
        record.setReportNo("AIR-KB-001");
        record.setReportType("SOP_QA");
        record.setBizNo("涂胶膜厚超限");
        record.setBizType("SOP_KB");
        record.setPromptTemplateVersion("rag-sop-qa-v2");
        record.setModelName("mock-rag-output");
        record.setModelProvider("LOCAL");
        record.setModelMode("SIMULATED");
        record.setModelConfigCode("MOCK_RAG_KEYWORD");
        record.setRetrievalStrategy("KEYWORD_FALLBACK");
        record.setEvidenceCount(2);
        record.setMaxEvidenceScore(0.91);
        record.setEvidenceLevel("HIGH");
        record.setInsufficientEvidence(0);
        record.setInputSnapshot("{\"question\":\"涂胶膜厚超限\"}");
        record.setOutputJson("{\"answer\":\"依据SOP处置\"}");
        record.setModelConfigSnapshot("{\"configCode\":\"MOCK_RAG_KEYWORD\"}");
        record.setStatus("SUCCESS");
        record.setCreatedBy("qe");
        record.setCreatedTime(LocalDateTime.of(2026, 6, 7, 20, 30));
        return record;
    }
}
