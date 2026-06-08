package com.visionox.mes.lot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * Release请求DTO
 */
@Data
@Schema(description = "Release请求")
public class ReleaseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Lot批次号（从路径参数自动获取）")
    private String lotNo;

    @Schema(description = "处置结果", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "处置结果不能为空")
    private String disposition;

    @Schema(description = "Release操作人")
    private String releaseBy;
}
