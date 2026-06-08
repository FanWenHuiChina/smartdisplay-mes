package com.visionox.mes.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.ai.entity.AiKbChunk;
import com.visionox.mes.ai.entity.AiKbDocument;
import com.visionox.mes.ai.mapper.AiKbChunkMapper;
import com.visionox.mes.ai.mapper.AiKbDocumentMapper;
import com.visionox.mes.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 试点版SOP/RAG检索服务。
 */
@Service
@RequiredArgsConstructor
public class AiKnowledgeService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong NO_COUNTER = new AtomicLong();
    private static final int TARGET_CHUNK_SIZE = 520;
    private static final int MAX_CHUNK_SIZE = 900;
    private static final String RETRIEVAL_STRATEGY = "KEYWORD_FALLBACK";
    private static final String HYBRID_RETRIEVAL_STRATEGY = "HYBRID_LOCAL";
    private static final int LOCAL_VECTOR_DIMENSION = 64;
    private static final double MIN_SUFFICIENT_EVIDENCE_SCORE = 0.65;
    private static final double MEDIUM_EVIDENCE_SCORE = 0.75;
    private static final double HIGH_EVIDENCE_SCORE = 0.85;

    private static final List<String> DOMAIN_TERMS = List.of(
            "hold", "release", "rework", "scrap", "recipe", "route", "lot", "mrb", "eap", "sop",
            "异常", "放行", "返工", "报废", "隔离", "复判", "处置", "审批", "设备", "报警",
            "蒸镀", "真空", "涂胶", "膜厚", "mura", "缺陷", "质量", "判定", "物料", "批次",
            "齐套", "追溯", "工单", "良率", "参数", "上限", "下限"
    );

    private final AiKbDocumentMapper documentMapper;
    private final AiKbChunkMapper chunkMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importDocument(Map<String, Object> request, String operator) {
        String content = text(request, "content", "");
        if (content.isBlank()) {
            throw new BusinessException("导入SOP文档内容不能为空");
        }
        if (content.length() < 20) {
            throw new BusinessException("导入SOP文档内容过短，无法形成有效知识切片");
        }

        String documentName = text(request, "documentName", text(request, "title", "导入SOP文档"));
        String documentType = text(request, "documentType", "SOP").trim().toUpperCase(Locale.ROOT);
        String docVersion = text(request, "docVersion", "V1.0");
        String documentNo = text(request, "documentNo", nextNo(documentType.replaceAll("[^A-Z0-9]+", "-")));
        LocalDateTime now = LocalDateTime.now();

        AiKbDocument document = documentMapper.selectOne(new LambdaQueryWrapper<AiKbDocument>()
                .eq(AiKbDocument::getDocumentNo, documentNo));
        boolean update = document != null;
        if (document == null) {
            document = new AiKbDocument();
            document.setDocumentNo(documentNo);
            document.setCreatedBy(valueOr(operator, "system"));
            document.setCreatedTime(now);
        }
        document.setDocumentName(documentName);
        document.setDocumentType(documentType);
        document.setDocVersion(docVersion);
        document.setStatus(text(request, "status", "ACTIVE").trim().toUpperCase(Locale.ROOT));
        document.setSourceUri(text(request, "sourceUri", "manual-import://" + documentNo));
        document.setOwnerRole(text(request, "ownerRole", "QE").trim().toUpperCase(Locale.ROOT));
        document.setUpdatedTime(now);
        if (update) {
            documentMapper.updateById(document);
            chunkMapper.delete(new LambdaQueryWrapper<AiKbChunk>().eq(AiKbChunk::getDocumentId, document.getId()));
        } else {
            documentMapper.insert(document);
        }

        List<String> chunks = splitContent(content);
        if (chunks.isEmpty()) {
            throw new BusinessException("导入SOP文档未生成有效切片");
        }

        List<Map<String, Object>> chunkRows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            AiKbChunk chunk = new AiKbChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkSeq(i + 1);
            chunk.setChunkNo(documentNo + "-" + String.format("%03d", i + 1));
            chunk.setChunkTitle(chunkTitle(documentName, chunks.get(i), i + 1));
            chunk.setContent(chunks.get(i));
            chunk.setKeywords(keywords(documentName + " " + chunks.get(i) + " " + documentType));
            chunk.setStatus("ACTIVE");
            chunk.setRetrievalStrategy(RETRIEVAL_STRATEGY);
            chunk.setEmbeddingStatus("NOT_INDEXED");
            chunk.setCreatedTime(now);
            chunkMapper.insert(chunk);
            chunkRows.add(chunkRow(chunk));
        }

        Map<String, Object> result = documentRow(document);
        result.put("updated", update);
        result.put("chunkCount", chunkRows.size());
        result.put("chunks", chunkRows);
        return result;
    }

    public List<Map<String, Object>> documents() {
        return documentMapper.selectList(new LambdaQueryWrapper<AiKbDocument>()
                        .orderByDesc(AiKbDocument::getUpdatedTime))
                .stream()
                .map(document -> {
                    Map<String, Object> row = documentRow(document);
                    List<AiKbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiKbChunk>()
                            .eq(AiKbChunk::getDocumentId, document.getId())
                            .orderByAsc(AiKbChunk::getChunkSeq));
                    putIndexSummary(row, chunks);
                    return row;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> ask(String question) {
        List<Map<String, Object>> sources = searchSources(question, 3);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("question", question);
        output.put("sources", sources);
        putEvidenceMeta(output, sources);

        if (question == null || question.isBlank()) {
            output.put("answer", "请提供具体问题。");
            output.put("insufficientEvidence", true);
            return output;
        }
        if (sources.isEmpty() || Boolean.TRUE.equals(output.get("insufficientEvidence"))) {
            output.put("answer", "依据不足：知识库未检索到与问题直接相关的 SOP、设备手册或质量判定标准，请补充设备、工序、缺陷或处置场景。");
            output.put("insufficientEvidence", true);
            return output;
        }

        String evidence = sources.stream()
                .map(source -> String.valueOf(source.get("content")))
                .collect(Collectors.joining("；"));
        output.put("answer", "依据已导入的 SOP/手册片段：" + evidence + "。AI 仅提供辅助分析，不自动执行生产动作。");
        output.put("insufficientEvidence", false);
        return output;
    }

    public List<Map<String, Object>> searchSources(String question, int limit) {
        List<Match> matches = search(question, limit);
        if (matches.isEmpty()) {
            return List.of();
        }
        Set<Long> documentIds = matches.stream()
                .map(match -> match.chunk().getDocumentId())
                .collect(Collectors.toSet());
        Map<Long, AiKbDocument> documents = documentMapper.selectBatchIds(documentIds)
                .stream()
                .collect(Collectors.toMap(AiKbDocument::getId, Function.identity()));
        return matches.stream()
                .map(match -> sourceRow(match, documents.get(match.chunk().getDocumentId())))
                .collect(Collectors.toList());
    }

    private List<Match> search(String question, int limit) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        List<AiKbChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiKbChunk>()
                .eq(AiKbChunk::getStatus, "ACTIVE")
                .orderByAsc(AiKbChunk::getChunkSeq));
        return chunks.stream()
                .map(chunk -> matchForQuestion(question, chunk))
                .filter(match -> match.rawScore() > 0.0)
                .sorted(Comparator.comparingDouble(Match::rawScore).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    private List<String> splitContent(String content) {
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n").trim();
        List<String> blocks = new ArrayList<>();
        StringBuilder currentBlock = new StringBuilder();
        for (String line : normalized.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) {
                flushBlock(blocks, currentBlock);
                continue;
            }
            if (isHeading(trimmed) && currentBlock.length() > 0) {
                flushBlock(blocks, currentBlock);
            }
            if (currentBlock.length() > 0) {
                currentBlock.append("\n");
            }
            currentBlock.append(trimmed);
        }
        flushBlock(blocks, currentBlock);

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        for (String block : blocks) {
            if (block.length() > MAX_CHUNK_SIZE) {
                flushChunk(chunks, currentChunk);
                chunks.addAll(splitLongBlock(block));
                continue;
            }
            if (currentChunk.length() > 0 && currentChunk.length() + block.length() + 2 > TARGET_CHUNK_SIZE) {
                flushChunk(chunks, currentChunk);
            }
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(block);
        }
        flushChunk(chunks, currentChunk);
        return chunks.stream()
                .map(String::trim)
                .filter(chunk -> chunk.length() >= 10)
                .collect(Collectors.toList());
    }

    private void flushBlock(List<String> blocks, StringBuilder currentBlock) {
        if (currentBlock.length() > 0) {
            blocks.add(currentBlock.toString().trim());
            currentBlock.setLength(0);
        }
    }

    private void flushChunk(List<String> chunks, StringBuilder currentChunk) {
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
            currentChunk.setLength(0);
        }
    }

    private List<String> splitLongBlock(String block) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : block.split("(?<=[。！？；.!?;])")) {
            String trimmed = sentence.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + trimmed.length() > TARGET_CHUNK_SIZE) {
                flushChunk(chunks, current);
            }
            current.append(trimmed);
        }
        flushChunk(chunks, current);
        return chunks;
    }

    private boolean isHeading(String line) {
        return line.startsWith("#") || line.matches("^[一二三四五六七八九十0-9]+[、.．].+");
    }

    private String chunkTitle(String documentName, String content, int seq) {
        return content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> line.replaceFirst("^#+\\s*", ""))
                .filter(line -> line.length() <= 60)
                .orElse(documentName + " / 片段" + seq);
    }

    private String keywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();
        String normalized = normalize(text);
        for (String term : DOMAIN_TERMS) {
            String normalizedTerm = normalize(term);
            if (!normalizedTerm.isBlank() && normalized.contains(normalizedTerm)) {
                keywords.add(term);
            }
        }
        extractTerms(normalized).stream()
                .filter(term -> term.length() >= 2)
                .limit(12)
                .forEach(keywords::add);
        return keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(32)
                .collect(Collectors.joining(" "));
    }

    private Map<String, Object> sourceRow(Match match, AiKbDocument document) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("documentNo", document == null ? "-" : document.getDocumentNo());
        row.put("documentName", document == null ? "-" : document.getDocumentName());
        row.put("documentType", document == null ? "-" : document.getDocumentType());
        row.put("chunkNo", match.chunk().getChunkNo());
        row.put("chunkTitle", match.chunk().getChunkTitle());
        double evidenceScore = confidence(match.rawScore());
        row.put("score", evidenceScore);
        row.put("evidenceScore", evidenceScore);
        row.put("evidenceLevel", evidenceLevel(evidenceScore, 1));
        row.put("retrievalStrategy", retrievalStrategy(match.chunk()));
        row.put("indexStrategy", retrievalStrategy(match.chunk()));
        row.put("rawScore", round(match.rawScore()));
        row.put("keywordScore", round(match.keywordScore()));
        row.put("vectorScore", round(match.vectorScore()));
        row.put("content", match.chunk().getContent());
        row.put("sourceUri", document == null ? "" : document.getSourceUri());
        return row;
    }

    private Map<String, Object> documentRow(AiKbDocument document) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("documentNo", document.getDocumentNo());
        row.put("documentName", document.getDocumentName());
        row.put("documentType", document.getDocumentType());
        row.put("docVersion", document.getDocVersion());
        row.put("status", document.getStatus());
        row.put("sourceUri", document.getSourceUri());
        row.put("ownerRole", document.getOwnerRole());
        row.put("createdBy", document.getCreatedBy());
        row.put("createdTime", document.getCreatedTime());
        row.put("updatedTime", document.getUpdatedTime());
        return row;
    }

    private Map<String, Object> chunkRow(AiKbChunk chunk) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("chunkNo", chunk.getChunkNo());
        row.put("chunkTitle", chunk.getChunkTitle());
        row.put("chunkSeq", chunk.getChunkSeq());
        row.put("keywords", chunk.getKeywords());
        row.put("retrievalStrategy", retrievalStrategy(chunk));
        row.put("indexStrategy", retrievalStrategy(chunk));
        row.put("embeddingStatus", valueOr(chunk.getEmbeddingStatus(), "NOT_INDEXED"));
        row.put("content", chunk.getContent());
        return row;
    }

    private void putEvidenceMeta(Map<String, Object> output, List<Map<String, Object>> sources) {
        int evidenceCount = sources == null ? 0 : sources.size();
        double maxEvidenceScore = sources == null ? 0.0 : sources.stream()
                .mapToDouble(source -> doubleValue(source.get("score")))
                .max()
                .orElse(0.0);
        String evidenceLevel = evidenceLevel(maxEvidenceScore, evidenceCount);
        output.put("retrievalStrategy", dominantRetrievalStrategy(sources));
        output.put("evidenceCount", evidenceCount);
        output.put("maxEvidenceScore", maxEvidenceScore);
        output.put("confidence", maxEvidenceScore);
        output.put("evidenceLevel", evidenceLevel);
        output.put("evidenceType", evidenceType(evidenceLevel));
        output.put("insufficientEvidence", "INSUFFICIENT".equals(evidenceLevel));
    }

    private void putIndexSummary(Map<String, Object> row, List<AiKbChunk> chunks) {
        List<AiKbChunk> safeChunks = chunks == null ? List.of() : chunks;
        long keywordIndexed = safeChunks.stream()
                .filter(chunk -> "KEYWORD_INDEXED".equals(valueOr(chunk.getEmbeddingStatus(), "")))
                .count();
        long hybridIndexed = safeChunks.stream()
                .filter(chunk -> "LOCAL_VECTOR_INDEXED".equals(valueOr(chunk.getEmbeddingStatus(), "")))
                .count();
        long vectorPending = safeChunks.stream()
                .filter(chunk -> "VECTOR_PENDING".equals(valueOr(chunk.getEmbeddingStatus(), "")))
                .count();
        long notIndexed = safeChunks.stream()
                .filter(chunk -> {
                    String status = valueOr(chunk.getEmbeddingStatus(), "NOT_INDEXED");
                    return "NOT_INDEXED".equals(status) || status.isBlank();
                })
                .count();
        row.put("chunkCount", safeChunks.size());
        row.put("indexedChunkCount", keywordIndexed);
        row.put("hybridIndexedChunkCount", hybridIndexed);
        row.put("vectorPendingChunkCount", vectorPending);
        row.put("notIndexedChunkCount", notIndexed);
        row.put("lastIndexedTime", safeChunks.stream()
                .map(AiKbChunk::getLastIndexedTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        String indexStatus = documentIndexStatus(safeChunks.size(), keywordIndexed, hybridIndexed, vectorPending, notIndexed);
        row.put("indexStatus", indexStatus);
        row.put("indexType", indexStatusType(indexStatus));
    }

    private String documentIndexStatus(int chunkCount, long keywordIndexed, long hybridIndexed, long vectorPending, long notIndexed) {
        if (chunkCount == 0) {
            return "NO_CHUNK";
        }
        if (hybridIndexed == chunkCount) {
            return "HYBRID_LOCAL";
        }
        if (vectorPending == chunkCount) {
            return "VECTOR_PENDING";
        }
        if (keywordIndexed == chunkCount) {
            return "KEYWORD_INDEXED";
        }
        if (notIndexed == chunkCount) {
            return "NOT_INDEXED";
        }
        return "PARTIAL_INDEXED";
    }

    private String indexStatusType(String indexStatus) {
        return switch (valueOr(indexStatus, "NOT_INDEXED")) {
            case "KEYWORD_INDEXED", "HYBRID_LOCAL" -> "green";
            case "VECTOR_PENDING", "PARTIAL_INDEXED" -> "amber";
            case "NO_CHUNK" -> "gray";
            default -> "red";
        };
    }

    private Match matchForQuestion(String question, AiKbChunk chunk) {
        double keywordScore = keywordScore(question, chunk);
        double vectorScore = isHybridLocal(chunk) ? localVectorSimilarity(question, chunk) : 0.0;
        double hybridBoost = isHybridLocal(chunk) && (keywordScore > 0.0 || vectorScore >= 0.45)
                ? Math.max(0.0, vectorScore - 0.20) * 8.0
                : 0.0;
        return new Match(chunk, keywordScore + hybridBoost, keywordScore, vectorScore);
    }

    private double keywordScore(String question, AiKbChunk chunk) {
        String normalizedQuestion = normalize(question);
        String content = normalize(chunk.getChunkTitle() + " " + chunk.getContent() + " " + chunk.getKeywords());
        String keywords = normalize(chunk.getKeywords());
        double score = 0.0;
        if (content.contains(normalizedQuestion)) {
            score += 8.0;
        }
        for (String term : extractTerms(normalizedQuestion)) {
            if (term.length() < 2) {
                continue;
            }
            if (content.contains(term)) {
                score += 2.0;
            }
            if (keywords.contains(term)) {
                score += 2.0;
            }
        }
        for (String term : DOMAIN_TERMS) {
            String normalizedTerm = normalize(term);
            if (normalizedQuestion.contains(normalizedTerm) && content.contains(normalizedTerm)) {
                score += 3.0;
            }
        }
        return score;
    }

    private double localVectorSimilarity(String question, AiKbChunk chunk) {
        String chunkText = normalize(chunk.getChunkTitle() + " " + chunk.getContent() + " " + chunk.getKeywords());
        double[] queryVector = localVector(normalize(question));
        double[] chunkVector = localVector(chunkText);
        return cosine(queryVector, chunkVector);
    }

    private double[] localVector(String text) {
        double[] vector = new double[LOCAL_VECTOR_DIMENSION];
        for (String term : extractTerms(text)) {
            addVectorFeature(vector, term, 1.0);
            if (term.length() > 3) {
                for (int i = 0; i <= term.length() - 3; i++) {
                    addVectorFeature(vector, term.substring(i, i + 3), 0.35);
                }
            }
        }
        for (String term : DOMAIN_TERMS) {
            String normalizedTerm = normalize(term);
            if (!normalizedTerm.isBlank() && text.contains(normalizedTerm)) {
                addVectorFeature(vector, normalizedTerm, 2.0);
            }
        }
        return vector;
    }

    private void addVectorFeature(double[] vector, String feature, double weight) {
        if (feature == null || feature.isBlank()) {
            return;
        }
        int hash = feature.hashCode();
        int index = Math.floorMod(hash, vector.length);
        int sign = (hash & 1) == 0 ? 1 : -1;
        vector[index] += sign * weight;
    }

    private double cosine(double[] left, double[] right) {
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm))));
    }

    private List<String> extractTerms(String text) {
        List<String> terms = new ArrayList<>();
        for (String term : text.split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        return terms;
    }

    private double confidence(double rawScore) {
        return Math.min(0.95, Math.round((0.45 + rawScore / 20.0) * 100.0) / 100.0);
    }

    private String evidenceLevel(double maxEvidenceScore, int evidenceCount) {
        if (evidenceCount <= 0 || maxEvidenceScore < MIN_SUFFICIENT_EVIDENCE_SCORE) {
            return "INSUFFICIENT";
        }
        if (maxEvidenceScore >= HIGH_EVIDENCE_SCORE && evidenceCount >= 2) {
            return "HIGH";
        }
        if (maxEvidenceScore >= MEDIUM_EVIDENCE_SCORE) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String evidenceType(String evidenceLevel) {
        return switch (valueOr(evidenceLevel, "INSUFFICIENT")) {
            case "HIGH" -> "green";
            case "MEDIUM" -> "blue";
            case "LOW" -> "amber";
            default -> "red";
        };
    }

    private String dominantRetrievalStrategy(List<Map<String, Object>> sources) {
        if (sources == null || sources.isEmpty()) {
            return RETRIEVAL_STRATEGY;
        }
        return sources.stream()
                .map(source -> String.valueOf(source.getOrDefault("retrievalStrategy", RETRIEVAL_STRATEGY)))
                .filter(strategy -> !strategy.isBlank())
                .findFirst()
                .orElse(RETRIEVAL_STRATEGY);
    }

    private boolean isHybridLocal(AiKbChunk chunk) {
        return HYBRID_RETRIEVAL_STRATEGY.equals(retrievalStrategy(chunk));
    }

    private String retrievalStrategy(AiKbChunk chunk) {
        String strategy = chunk == null ? "" : valueOr(chunk.getRetrievalStrategy(), RETRIEVAL_STRATEGY);
        if (HYBRID_RETRIEVAL_STRATEGY.equalsIgnoreCase(strategy)
                || "LOCAL_VECTOR_INDEXED".equalsIgnoreCase(chunk == null ? "" : valueOr(chunk.getEmbeddingStatus(), ""))) {
            return HYBRID_RETRIEVAL_STRATEGY;
        }
        return "PGVECTOR_READY".equalsIgnoreCase(strategy) ? "PGVECTOR_READY" : RETRIEVAL_STRATEGY;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
    }

    private String nextNo(String prefix) {
        String safePrefix = valueOr(prefix, "SOP").replaceAll("-+$", "");
        long seq = NO_COUNTER.updateAndGet(value -> value >= 9999 ? 1 : value + 1);
        return safePrefix + "-IMPORT-" + NO_TIME.format(LocalDateTime.now()) + "-" + String.format("%04d", seq);
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = request == null ? null : request.get(key);
        return value == null || String.valueOf(value).isBlank() ? valueOr(defaultValue, "") : String.valueOf(value);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Match(AiKbChunk chunk, double rawScore, double keywordScore, double vectorScore) {
    }
}
