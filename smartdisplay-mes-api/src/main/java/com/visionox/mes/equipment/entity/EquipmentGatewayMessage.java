package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("equipment_gateway_message")
public class EquipmentGatewayMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageNo;
    private String gatewayCode;
    private String equipmentCode;
    private String protocolType;
    private String driverCode;
    private String direction;
    private String messageType;
    private String correlationId;
    private String processStatus;
    private String errorMessage;
    private LocalDateTime occurredTime;
    private LocalDateTime processedTime;
    private String payloadSnapshot;
    private String normalizedPayloadSnapshot;
    private String responseSnapshot;
    private LocalDateTime createdTime;
}
