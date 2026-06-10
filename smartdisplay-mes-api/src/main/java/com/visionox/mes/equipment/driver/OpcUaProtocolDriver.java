package com.visionox.mes.equipment.driver;

import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class OpcUaProtocolDriver extends AbstractEapProtocolDriver {

    @Override
    public String protocolType() {
        return "OPC_UA";
    }

    @Override
    public String driverCode() {
        return "opc-ua-driver";
    }

    @Override
    protected String description() {
        return "OPC UA 影子协议驱动，负责校验节点数据变化并归一为 MES 标准消息。";
    }

    @Override
    protected boolean requiresRealEquipmentLink() {
        return true;
    }

    @Override
    public Map<String, Object> normalizeInbound(EquipmentGatewayConnection gateway, Map<String, Object> request) {
        Map<String, Object> envelope = safeMap(request);
        Map<String, Object> normalized = super.normalizeInbound(gateway, request);
        String operation = text(envelope, "operation", text(envelope, "eventType", ""));
        if ("UNKNOWN".equals(normalized.get("messageType")) && !operation.isBlank()) {
            normalized.put("messageType", normalizeMessageType(operation));
        }
        if ("UNKNOWN".equals(normalized.get("messageType"))) {
            normalized.put("messageType", "PARAMETER");
        }
        return normalized;
    }

    @Override
    protected void validateInbound(EquipmentGatewayConnection gateway, Map<String, Object> envelope,
                                   Map<String, Object> payload) {
        requireAnyText("OPC UA node identity is missing", envelope, payload, "nodeId");
        requireAnyText("OPC UA equipment identity is missing", envelope, payload, "equipmentCode");
    }

    @Override
    protected Map<String, Object> enrichPayload(Map<String, Object> payload, Map<String, Object> envelope) {
        Map<String, Object> enriched = super.enrichPayload(payload, envelope);
        copyIfAbsent(enriched, envelope, "nodeId");
        copyIfAbsent(enriched, envelope, "browseName");
        copyIfAbsent(enriched, envelope, "operation");
        copyIfAbsent(enriched, envelope, "eventType");
        copyIfAbsent(enriched, envelope, "namespaceIndex");
        copyIfAbsent(enriched, envelope, "monitoredItemId");
        copyIfAbsent(enriched, envelope, "qualityCode");
        copyIfAbsent(enriched, envelope, "sourceTimestamp");
        copyIfAbsent(enriched, envelope, "serverTimestamp");
        return enriched;
    }
}
