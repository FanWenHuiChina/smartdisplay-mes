package com.visionox.mes.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI分析结果留痕记录。
 */
@Data
@TableName("ai_report_record")
public class AiReportRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reportNo;

    private String reportType;

    private String bizNo;

    private String bizType;

    private String promptTemplateVersion;

    private String modelName;

    private String modelProvider;

    private String modelMode;

    private String modelConfigCode;

    private String retrievalStrategy;

    private Integer evidenceCount;

    private Double maxEvidenceScore;

    private String evidenceLevel;

    private Integer insufficientEvidence;

    private String modelConfigSnapshot;

    private String inputSnapshot;

    private String outputJson;

    private String status;

    private String createdBy;

    private LocalDateTime createdTime;
}
