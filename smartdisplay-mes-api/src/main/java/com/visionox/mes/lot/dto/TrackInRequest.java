package com.visionox.mes.lot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * Track In请求DTO
 */
@Data
@Schema(description = "Track In请求")
public class TrackInRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Lot批次号（从路径参数自动获取）")
    private String lotNo;

    @Schema(description = "工序编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "工序编码不能为空")
    private String stepCode;

    @Schema(description = "设备编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备编码不能为空")
    private String equipmentCode;

    @Schema(description = "操作员")
    private String operator;
}
