package com.visionox.mes.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI知识库索引任务履历。
 */
@Data
@TableName("ai_kb_index_job")
public class AiKbIndexJob implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobNo;

    private String jobType;

    private String documentNo;

    private String retrievalStrategy;

    private String embeddingModel;

    private String status;

    private Integer targetChunkCount;

    private Integer indexedChunkCount;

    private Integer failedChunkCount;

    private String boundaryNote;

    private String failureReason;

    private String triggeredBy;

    private LocalDateTime startedTime;

    private LocalDateTime finishedTime;

    private LocalDateTime createdTime;
}
