package com.visionox.mes.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI知识库切片。
 */
@Data
@TableName("ai_kb_chunk")
public class AiKbChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long documentId;

    private String chunkNo;

    private String chunkTitle;

    private Integer chunkSeq;

    private String content;

    private String keywords;

    private String status;

    private String retrievalStrategy;

    private String embeddingStatus;

    private String embeddingModel;

    private String embeddingRef;

    private LocalDateTime lastIndexedTime;

    private LocalDateTime createdTime;
}
