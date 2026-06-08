package com.visionox.mes.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI知识库文档。
 */
@Data
@TableName("ai_kb_document")
public class AiKbDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String documentNo;

    private String documentName;

    private String documentType;

    private String docVersion;

    private String status;

    private String sourceUri;

    private String ownerRole;

    private String createdBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
