package com.visionox.mes.equipment.driver;

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
        return "OPC UA 驱动占位，负责将节点订阅、方法调用和数据变化归一为 MES 标准消息。";
    }

    @Override
    protected boolean requiresRealEquipmentLink() {
        return true;
    }

    @Override
    protected Map<String, Object> enrichPayload(Map<String, Object> payload, Map<String, Object> envelope) {
        Map<String, Object> enriched = super.enrichPayload(payload, envelope);
        copyIfAbsent(enriched, envelope, "nodeId");
        copyIfAbsent(enriched, envelope, "namespaceIndex");
        copyIfAbsent(enriched, envelope, "monitoredItemId");
        copyIfAbsent(enriched, envelope, "qualityCode");
        return enriched;
    }
}
