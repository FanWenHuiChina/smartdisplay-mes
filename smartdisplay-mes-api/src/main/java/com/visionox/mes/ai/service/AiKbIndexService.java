package com.visionox.mes.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.ai.entity.AiKbChunk;
import com.visionox.mes.ai.entity.AiKbDocument;
import com.visionox.mes.ai.entity.AiKbIndexJob;
import com.visionox.mes.ai.mapper.AiKbChunkMapper;
import com.visionox.mes.ai.mapper.AiKbDocumentMapper;
import com.visionox.mes.ai.mapper.AiKbIndexJobMapper;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * AI知识库索引任务服务。
 *
 * <p>当前只完成关键词 fallback 索引和 pgvector-ready 元数据标记，不生成真实 embedding。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiKbIndexService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong NO_COUNTER = new AtomicLong();
    private static final List<String> SUPPORTED_STRATEGIES = List.of("KEYWORD_FALLBACK", "HYBRID_LOCAL", "PGVECTOR_READY");

    private final AiKbIndexJobMapper indexJobMapper;
    private final AiKbDocumentMapper documentMapper;
    private final AiKbChunkMapper chunkMapper;
    private final AuditLogService auditLogService;

    public List<Map<String, Object>> jobs(String documentNo, String status) {
        LambdaQueryWrapper<AiKbIndexJob> wrapper = new LambdaQueryWrapper<>();
        if (notBlank(documentNo)) {
            wrapper.eq(AiKbIndexJob::getDocumentNo, documentNo.trim());
        }
        if (notBlank(status)) {
            wrapper.eq(AiKbIndexJob::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(AiKbIndexJob::getCreatedTime).last("LIMIT 80");
        return indexJobMapper.selectList(wrapper).stream()
                .map(this::row)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createIndexJob(Map<String, Object> request, String operator) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String strategy = normalizeStrategy(text(safeRequest, "retrievalStrategy", "KEYWORD_FALLBACK"));
        String documentNo = text(safeRequest, "documentNo", "");
        String jobType = normalizeJobType(text(safeRequest, "jobType", "MANUAL_REINDEX"));
        String embeddingModel = text(safeRequest, "embeddingModel", defaultEmbeddingModel(strategy));
        String currentOperator = valueOr(operator, "system");

        AiKbDocument document = null;
        LambdaQueryWrapper<AiKbChunk> chunkWrapper = new LambdaQueryWrapper<AiKbChunk>()
                .eq(AiKbChunk::getStatus, "ACTIVE")
                .orderByAsc(AiKbChunk::getDocumentId)
                .orderByAsc(AiKbChunk::getChunkSeq);
        if (notBlank(documentNo)) {
            document = documentMapper.selectOne(new LambdaQueryWrapper<AiKbDocument>()
                    .eq(AiKbDocument::getDocumentNo, documentNo.trim()));
            if (document == null) {
                throw new BusinessException("知识库文档不存在: " + documentNo);
            }
            chunkWrapper.eq(AiKbChunk::getDocumentId, document.getId());
        }

        List<AiKbChunk> chunks = chunkMapper.selectList(chunkWrapper);
        if (chunks == null || chunks.isEmpty()) {
            throw new BusinessException(notBlank(documentNo)
                    ? "该知识库文档没有可索引切片: " + documentNo
                    : "知识库没有可索引切片");
        }

        LocalDateTime now = LocalDateTime.now();
        AiKbIndexJob job = new AiKbIndexJob();
        job.setJobNo(nextJobNo());
        job.setJobType(jobType);
        job.setDocumentNo(document == null ? null : document.getDocumentNo());
        job.setRetrievalStrategy(strategy);
        job.setEmbeddingModel(embeddingModel);
        job.setStatus("RUNNING");
        job.setTargetChunkCount(chunks.size());
        job.setIndexedChunkCount(0);
        job.setFailedChunkCount(0);
        job.setBoundaryNote(boundaryNote(strategy));
        job.setTriggeredBy(currentOperator);
        job.setStartedTime(now);
        job.setCreatedTime(now);
        indexJobMapper.insert(job);

        int indexedCount = 0;
        LocalDateTime indexedTime = LocalDateTime.now();
        for (AiKbChunk chunk : chunks) {
            chunk.setRetrievalStrategy(strategy);
            chunk.setEmbeddingStatus(embeddingStatus(strategy));
            chunk.setEmbeddingModel(embeddingModel);
            chunk.setEmbeddingRef(embeddingRef(strategy, chunk.getChunkNo()));
            chunk.setLastIndexedTime(indexedTime);
            chunkMapper.updateById(chunk);
            indexedCount++;
        }

        job.setStatus("COMPLETED");
        job.setIndexedChunkCount(indexedCount);
        job.setFailedChunkCount(0);
        job.setFinishedTime(LocalDateTime.now());
        indexJobMapper.updateById(job);
        recordAudit(job, currentOperator);
        return row(job);
    }

    private void recordAudit(AiKbIndexJob job, String operator) {
        try {
            auditLogService.record("AI_KB_INDEX", job.getJobNo(), "SOP_KB",
                    "重建AI知识库索引: " + job.getRetrievalStrategy() + ", 切片 " + job.getIndexedChunkCount() + "/" + job.getTargetChunkCount(),
                    operator, "ai-kb-index-service", auditSnapshot(job));
        } catch (Exception e) {
            log.warn("AI知识库索引审计写入失败: jobNo={}, reason={}", job.getJobNo(), e.getMessage());
        }
    }

    private Map<String, Object> row(AiKbIndexJob job) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("jobNo", job.getJobNo());
        row.put("jobType", job.getJobType());
        row.put("documentNo", valueOr(job.getDocumentNo(), "ALL"));
        row.put("retrievalStrategy", job.getRetrievalStrategy());
        row.put("embeddingModel", job.getEmbeddingModel());
        row.put("status", job.getStatus());
        row.put("targetChunkCount", valueOr(job.getTargetChunkCount(), 0));
        row.put("indexedChunkCount", valueOr(job.getIndexedChunkCount(), 0));
        row.put("failedChunkCount", valueOr(job.getFailedChunkCount(), 0));
        row.put("boundaryNote", job.getBoundaryNote());
        row.put("failureReason", job.getFailureReason());
        row.put("triggeredBy", valueOr(job.getTriggeredBy(), "system"));
        row.put("startedTime", job.getStartedTime());
        row.put("finishedTime", job.getFinishedTime());
        row.put("createdTime", job.getCreatedTime());
        row.put("time", job.getCreatedTime() == null ? "-" : job.getCreatedTime().toLocalTime().withNano(0).toString());
        row.put("type", statusType(job.getStatus(), job.getRetrievalStrategy()));
        row.put("hybridLocal", "HYBRID_LOCAL".equals(job.getRetrievalStrategy()));
        row.put("vectorReady", "PGVECTOR_READY".equals(job.getRetrievalStrategy()));
        return row;
    }

    private String auditSnapshot(AiKbIndexJob job) {
        return "{"
                + "\"jobNo\":\"" + job.getJobNo() + "\","
                + "\"documentNo\":\"" + valueOr(job.getDocumentNo(), "ALL") + "\","
                + "\"retrievalStrategy\":\"" + job.getRetrievalStrategy() + "\","
                + "\"embeddingModel\":\"" + valueOr(job.getEmbeddingModel(), "") + "\","
                + "\"status\":\"" + job.getStatus() + "\","
                + "\"targetChunkCount\":" + valueOr(job.getTargetChunkCount(), 0) + ","
                + "\"indexedChunkCount\":" + valueOr(job.getIndexedChunkCount(), 0) + ","
                + "\"boundary\":\"" + job.getBoundaryNote().replace("\"", "'") + "\""
                + "}";
    }

    private String normalizeStrategy(String strategy) {
        String normalized = valueOr(strategy, "KEYWORD_FALLBACK").trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STRATEGIES.contains(normalized)) {
            throw new BusinessException("不支持的知识库检索策略: " + strategy);
        }
        return normalized;
    }

    private String normalizeJobType(String jobType) {
        return valueOr(jobType, "MANUAL_REINDEX")
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_");
    }

    private String embeddingStatus(String strategy) {
        return switch (strategy) {
            case "PGVECTOR_READY" -> "VECTOR_PENDING";
            case "HYBRID_LOCAL" -> "LOCAL_VECTOR_INDEXED";
            default -> "KEYWORD_INDEXED";
        };
    }

    private String defaultEmbeddingModel(String strategy) {
        return switch (strategy) {
            case "PGVECTOR_READY" -> "external-embedding-not-configured";
            case "HYBRID_LOCAL" -> "local-hybrid-vector-v1";
            default -> "local-keyword-index-v1";
        };
    }

    private String embeddingRef(String strategy, String chunkNo) {
        String safeChunkNo = valueOr(chunkNo, "unknown");
        return switch (strategy) {
            case "PGVECTOR_READY" -> "pgvector-ready://pending/" + safeChunkNo;
            case "HYBRID_LOCAL" -> "local-vector://indexed/" + safeChunkNo;
            default -> "keyword://indexed/" + safeChunkNo;
        };
    }

    private String boundaryNote(String strategy) {
        if ("PGVECTOR_READY".equals(strategy)) {
            return "仅标记向量索引待联调状态；未生成真实embedding，未执行pgvector相似度检索。";
        }
        if ("HYBRID_LOCAL".equals(strategy)) {
            return "已重建本地混合检索索引：关键词召回 + 本地确定性向量指纹评分；不调用外部模型，不自动执行生产动作。";
        }
        return "已重建本地关键词fallback索引；RAG仍由关键词召回提供证据。";
    }

    private String statusType(String status, String strategy) {
        if ("FAILED".equals(status)) {
            return "red";
        }
        if ("RUNNING".equals(status)) {
            return "blue";
        }
        return "PGVECTOR_READY".equals(strategy) ? "amber" : "green";
    }

    private String nextJobNo() {
        long seq = NO_COUNTER.updateAndGet(value -> value >= 9999 ? 1 : value + 1);
        return "AI-KB-IDX-" + NO_TIME.format(LocalDateTime.now()) + "-" + String.format("%04d", seq);
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = request == null ? null : request.get(key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Integer valueOr(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }
}
