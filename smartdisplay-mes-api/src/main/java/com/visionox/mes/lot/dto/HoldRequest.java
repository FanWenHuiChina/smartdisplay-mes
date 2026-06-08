package com.visionox.mes.lot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * Hold请求DTO
 */
@Data
@Schema(description = "Hold请求")
public class HoldRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Lot批次号（从路径参数自动获取）")
    private String lotNo;

    @Schema(description = "Hold原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Hold原因不能为空")
    private String holdReason;

    @Schema(description = "Hold类型: QUALITY-质量异常, EQUIPMENT-设备故障, MATERIAL-物料问题, ENGINEERING-工程变更")
    private String holdType;

    @Schema(description = "Hold操作人")
    private String holdBy;
}
