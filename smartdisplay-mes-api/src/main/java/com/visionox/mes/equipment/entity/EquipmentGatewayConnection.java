package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("equipment_gateway_connection")
public class EquipmentGatewayConnection implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String gatewayCode;
    private String gatewayName;
    private String protocolType;
    private String driverCode;
    private String driverMode;
    private String endpointUri;
    private String lineCode;
    private String equipmentCodes;
    private String status;
    private Integer heartbeatIntervalMs;
    private Integer tlsEnabled;
    private Integer connectionTimeoutMs;
    private Integer readTimeoutMs;
    private LocalDateTime lastHeartbeatTime;
    private String lastError;
    private Integer enabled;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String driverConfigSnapshot;
    private String requestSnapshot;
}
