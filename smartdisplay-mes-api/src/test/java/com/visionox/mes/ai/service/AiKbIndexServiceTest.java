package com.visionox.mes.ai.service;

import com.visionox.mes.ai.entity.AiKbChunk;
import com.visionox.mes.ai.entity.AiKbDocument;
import com.visionox.mes.ai.entity.AiKbIndexJob;
import com.visionox.mes.ai.mapper.AiKbChunkMapper;
import com.visionox.mes.ai.mapper.AiKbDocumentMapper;
import com.visionox.mes.ai.mapper.AiKbIndexJobMapper;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKbIndexServiceTest {

    @Mock
    private AiKbIndexJobMapper indexJobMapper;

    @Mock
    private AiKbDocumentMapper documentMapper;

    @Mock
    private AiKbChunkMapper chunkMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AiKbIndexService service;

    @Test
    void createIndexJobShouldMarkKeywordIndexAndAudit() {
        AiKbDocument document = document("SOP-HOLD-001");
        AiKbChunk first = chunk("SOP-HOLD-001-001");
        AiKbChunk second = chunk("SOP-HOLD-001-002");
        when(documentMapper.selectOne(any())).thenReturn(document);
        when(chunkMapper.selectList(any())).thenReturn(List.of(first, second));
        doAnswer(invocation -> {
            AiKbIndexJob job = invocation.getArgument(0);
            job.setId(9L);
            return 1;
        }).when(indexJobMapper).insert(any(AiKbIndexJob.class));

        Map<String, Object> result = service.createIndexJob(Map.of(
                "documentNo", "SOP-HOLD-001",
                "retrievalStrategy", "KEYWORD_FALLBACK"
        ), "qe1001");

        verify(chunkMapper).updateById(first);
        verify(chunkMapper).updateById(second);
        assertThat(first.getEmbeddingStatus()).isEqualTo("KEYWORD_INDEXED");
        assertThat(first.getEmbeddingModel()).isEqualTo("local-keyword-index-v1");
        assertThat(first.getEmbeddingRef()).isEqualTo("keyword://indexed/SOP-HOLD-001-001");
        assertThat(second.getEmbeddingStatus()).isEqualTo("KEYWORD_INDEXED");

        ArgumentCaptor<AiKbIndexJob> jobCaptor = ArgumentCaptor.forClass(AiKbIndexJob.class);
        verify(indexJobMapper).updateById(jobCaptor.capture());
        AiKbIndexJob completedJob = jobCaptor.getValue();
        assertThat(completedJob.getStatus()).isEqualTo("COMPLETED");
        assertThat(completedJob.getTargetChunkCount()).isEqualTo(2);
        assertThat(completedJob.getIndexedChunkCount()).isEqualTo(2);
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        assertThat(result.get("documentNo")).isEqualTo("SOP-HOLD-001");
        assertThat(result.get("type")).isEqualTo("green");

        verify(auditLogService).record(eq("AI_KB_INDEX"), eq(completedJob.getJobNo()), eq("SOP_KB"),
                contains("KEYWORD_FALLBACK"), eq("qe1001"), eq("ai-kb-index-service"), contains("indexedChunkCount"));
    }

    @Test
    void createIndexJobShouldMarkPgvectorReadyAsPendingBoundary() {
        AiKbChunk chunk = chunk("MANUAL-EVAP-001-001");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        when(indexJobMapper.insert(any(AiKbIndexJob.class))).thenReturn(1);

        Map<String, Object> result = service.createIndexJob(Map.of(
                "retrievalStrategy", "PGVECTOR_READY",
                "embeddingModel", "text-embedding-not-configured"
        ), "ee1001");

        assertThat(chunk.getEmbeddingStatus()).isEqualTo("VECTOR_PENDING");
        assertThat(chunk.getEmbeddingModel()).isEqualTo("text-embedding-not-configured");
        assertThat(chunk.getEmbeddingRef()).isEqualTo("pgvector-ready://pending/MANUAL-EVAP-001-001");
        assertThat(result.get("documentNo")).isEqualTo("ALL");
        assertThat(result.get("type")).isEqualTo("amber");
        assertThat(String.valueOf(result.get("boundaryNote"))).contains("未生成真实embedding");
    }

    @Test
    void createIndexJobShouldBuildHybridLocalIndex() {
        AiKbChunk chunk = chunk("SOP-HYBRID-001-001");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        when(indexJobMapper.insert(any(AiKbIndexJob.class))).thenReturn(1);

        Map<String, Object> result = service.createIndexJob(Map.of("retrievalStrategy", "HYBRID_LOCAL"), "qe1001");

        assertThat(chunk.getRetrievalStrategy()).isEqualTo("HYBRID_LOCAL");
        assertThat(chunk.getEmbeddingStatus()).isEqualTo("LOCAL_VECTOR_INDEXED");
        assertThat(chunk.getEmbeddingModel()).isEqualTo("local-hybrid-vector-v1");
        assertThat(chunk.getEmbeddingRef()).isEqualTo("local-vector://indexed/SOP-HYBRID-001-001");
        assertThat(result.get("hybridLocal")).isEqualTo(true);
        assertThat(result.get("vectorReady")).isEqualTo(false);
        assertThat(result.get("type")).isEqualTo("green");
        assertThat(String.valueOf(result.get("boundaryNote"))).contains("本地混合检索");
    }

    @Test
    void createIndexJobShouldRejectUnsupportedStrategy() {
        assertThatThrownBy(() -> service.createIndexJob(Map.of("retrievalStrategy", "VECTOR_SEARCH"), "pe1001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的知识库检索策略");
    }

    private AiKbDocument document(String documentNo) {
        AiKbDocument document = new AiKbDocument();
        document.setId(7L);
        document.setDocumentNo(documentNo);
        return document;
    }

    private AiKbChunk chunk(String chunkNo) {
        AiKbChunk chunk = new AiKbChunk();
        chunk.setId((long) Math.abs(chunkNo.hashCode()));
        chunk.setDocumentId(7L);
        chunk.setChunkNo(chunkNo);
        chunk.setChunkSeq(1);
        chunk.setStatus("ACTIVE");
        return chunk;
    }
}
