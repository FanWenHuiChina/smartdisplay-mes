package com.visionox.mes.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI模型运行配置。
 */
@Data
@TableName("ai_model_config")
public class AiModelConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configCode;

    private String configName;

    private String useCase;

    private String provider;

    private String modelName;

    private String modelMode;

    private String endpointUri;

    private String promptTemplateVersion;

    private String retrievalStrategy;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer timeoutMs;

    private Integer enabled;

    private String status;

    private String modelConfigSnapshot;

    private String createdBy;

    private LocalDateTime createdTime;

    private String updatedBy;

    private LocalDateTime updatedTime;
}
