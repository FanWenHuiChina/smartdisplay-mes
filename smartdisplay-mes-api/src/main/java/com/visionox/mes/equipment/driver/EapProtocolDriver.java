package com.visionox.mes.equipment.driver;

import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;

import java.util.Map;

/**
 * EAP 协议驱动边界：负责把具体协议入站帧归一化为 MES 标准消息。
 */
public interface EapProtocolDriver {

    String protocolType();

    String driverCode();

    Map<String, Object> capabilities();

    Map<String, Object> normalizeInbound(EquipmentGatewayConnection gateway, Map<String, Object> request);

    Map<String, Object> checkHealth(EquipmentGatewayConnection gateway, Map<String, Object> request);
}
