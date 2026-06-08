package com.visionox.mes.ai.service;

import com.visionox.mes.ai.entity.AiKbChunk;
import com.visionox.mes.ai.entity.AiKbDocument;
import com.visionox.mes.ai.mapper.AiKbChunkMapper;
import com.visionox.mes.ai.mapper.AiKbDocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiKnowledgeServiceTest {

    @Mock
    private AiKbDocumentMapper documentMapper;

    @Mock
    private AiKbChunkMapper chunkMapper;

    @InjectMocks
    private AiKnowledgeService aiKnowledgeService;

    @Test
    void importDocumentShouldCreateDocumentAndAutoChunks() {
        when(documentMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            AiKbDocument document = invocation.getArgument(0);
            document.setId(7L);
            return 1;
        }).when(documentMapper).insert(any(AiKbDocument.class));

        Map<String, Object> result = aiKnowledgeService.importDocument(Map.of(
                "documentNo", "SOP-COAT-IMPORT-001",
                "documentName", "涂胶膜厚异常处置SOP",
                "documentType", "SOP",
                "docVersion", "V1.1",
                "content", """
                        一、触发条件
                        涂胶膜厚超出Recipe上下限、连续两批Lot出现同类膜厚异常、或COATER设备压力波动影响当前Lot时，必须触发质量复判。

                        二、处置步骤
                        先Hold受影响Lot，记录设备、Recipe、物料批次和过程参数快照；QE完成MRB复判后选择放行、返工或报废。

                        三、关闭要求
                        关闭异常前必须填写根因、处置结论和复判人，AI仅提供SOP依据，不自动执行生产动作。
                        """
        ), "qe");

        ArgumentCaptor<AiKbChunk> chunkCaptor = ArgumentCaptor.forClass(AiKbChunk.class);
        verify(chunkMapper).insert(chunkCaptor.capture());
        AiKbChunk chunk = chunkCaptor.getValue();

        assertThat(result.get("documentNo")).isEqualTo("SOP-COAT-IMPORT-001");
        assertThat(result.get("chunkCount")).isEqualTo(1);
        assertThat(chunk.getDocumentId()).isEqualTo(7L);
        assertThat(chunk.getChunkNo()).isEqualTo("SOP-COAT-IMPORT-001-001");
        assertThat(chunk.getKeywords()).contains("涂胶", "mrb", "hold");
    }

    @Test
    void askShouldReturnImportedChunkAsEvidence() {
        AiKbDocument document = new AiKbDocument();
        document.setId(7L);
        document.setDocumentNo("SOP-COAT-IMPORT-001");
        document.setDocumentName("涂胶膜厚异常处置SOP");
        document.setDocumentType("SOP");
        document.setSourceUri("manual-import://SOP-COAT-IMPORT-001");

        AiKbChunk chunk = new AiKbChunk();
        chunk.setDocumentId(7L);
        chunk.setChunkNo("SOP-COAT-IMPORT-001-001");
        chunk.setChunkTitle("涂胶膜厚异常处置");
        chunk.setChunkSeq(1);
        chunk.setContent("涂胶膜厚超限时，应Hold受影响Lot，记录Recipe、设备和物料批次，并由QE完成MRB复判。");
        chunk.setKeywords("涂胶 膜厚 hold lot recipe 设备 物料 批次 QE mrb 复判");

        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        when(documentMapper.selectBatchIds(anyCollection())).thenReturn(List.of(document));

        Map<String, Object> answer = aiKnowledgeService.ask("涂胶膜厚超限时怎么Hold和MRB复判");

        assertThat(answer.get("insufficientEvidence")).isEqualTo(false);
        assertThat(answer.get("retrievalStrategy")).isEqualTo("KEYWORD_FALLBACK");
        assertThat(answer.get("evidenceCount")).isEqualTo(1);
        assertThat(answer.get("evidenceLevel")).isEqualTo("MEDIUM");
        assertThat((Double) answer.get("maxEvidenceScore")).isGreaterThanOrEqualTo(0.75);
        assertThat(String.valueOf(answer.get("answer"))).contains("涂胶膜厚超限");
        assertThat((List<?>) answer.get("sources")).hasSize(1);
    }

    @Test
    void askShouldMarkInsufficientWhenEvidenceScoreIsTooLow() {
        AiKbDocument document = new AiKbDocument();
        document.setId(8L);
        document.setDocumentNo("MANUAL-GENERAL-001");
        document.setDocumentName("设备通用手册");
        document.setDocumentType("EQUIPMENT_MANUAL");
        document.setSourceUri("manual-import://MANUAL-GENERAL-001");

        AiKbChunk chunk = new AiKbChunk();
        chunk.setDocumentId(8L);
        chunk.setChunkNo("MANUAL-GENERAL-001-001");
        chunk.setChunkTitle("设备通用说明");
        chunk.setChunkSeq(1);
        chunk.setContent("设备日常点检应保留记录。");
        chunk.setKeywords("设备 点检");

        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        when(documentMapper.selectBatchIds(anyCollection())).thenReturn(List.of(document));

        Map<String, Object> answer = aiKnowledgeService.ask("设备怎么处理");

        assertThat(answer.get("insufficientEvidence")).isEqualTo(true);
        assertThat(answer.get("evidenceLevel")).isEqualTo("INSUFFICIENT");
        assertThat(answer.get("evidenceCount")).isEqualTo(1);
        assertThat((Double) answer.get("maxEvidenceScore")).isLessThan(0.65);
        assertThat(String.valueOf(answer.get("answer"))).contains("依据不足");
    }
}
