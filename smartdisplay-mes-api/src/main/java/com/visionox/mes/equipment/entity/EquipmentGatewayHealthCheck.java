package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("equipment_gateway_health_check")
public class EquipmentGatewayHealthCheck implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String checkNo;
    private String gatewayCode;
    private String protocolType;
    private String driverCode;
    private String endpointUri;
    private String checkType;
    private String resultStatus;
    private Integer latencyMs;
    private String errorMessage;
    private String checkedBy;
    private LocalDateTime checkedTime;
    private String requestSnapshot;
    private String responseSnapshot;
}
