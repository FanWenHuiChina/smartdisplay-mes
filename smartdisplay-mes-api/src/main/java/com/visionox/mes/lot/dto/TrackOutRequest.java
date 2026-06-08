package com.visionox.mes.lot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * Track Out请求DTO
 */
@Data
@Schema(description = "Track Out请求")
public class TrackOutRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Lot批次号（从路径参数自动获取）")
    private String lotNo;

    @Schema(description = "加工结果: OK-合格, NG-不合格", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "加工结果不能为空")
    private String result;

    @Schema(description = "加工参数(JSON字符串)")
    private String processParams;

    @Schema(description = "备注")
    private String remark;
}
