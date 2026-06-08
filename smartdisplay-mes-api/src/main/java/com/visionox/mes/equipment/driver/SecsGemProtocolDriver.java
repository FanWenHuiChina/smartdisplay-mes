package com.visionox.mes.equipment.driver;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class SecsGemProtocolDriver extends AbstractEapProtocolDriver {

    @Override
    public String protocolType() {
        return "SECS_GEM";
    }

    @Override
    public String driverCode() {
        return "secs-gem-driver";
    }

    @Override
    protected String description() {
        return "SECS/GEM 驱动占位，负责将 S/F 消息和 CEID/RPTID 数据归一为 MES 标准消息。";
    }

    @Override
    protected boolean requiresRealEquipmentLink() {
        return true;
    }

    @Override
    public Map<String, Object> normalizeInbound(com.visionox.mes.equipment.entity.EquipmentGatewayConnection gateway,
                                                Map<String, Object> request) {
        Map<String, Object> normalized = super.normalizeInbound(gateway, request);
        String streamFunction = secsMessageName(request);
        if ("UNKNOWN".equals(normalized.get("messageType")) && !streamFunction.isBlank()) {
            normalized.put("messageType", normalizeMessageType(streamFunction));
        }
        return normalized;
    }

    @Override
    protected Map<String, Object> enrichPayload(Map<String, Object> payload, Map<String, Object> envelope) {
        Map<String, Object> enriched = super.enrichPayload(payload, envelope);
        copyIfAbsent(enriched, envelope, "stream");
        copyIfAbsent(enriched, envelope, "function");
        copyIfAbsent(enriched, envelope, "ceid");
        copyIfAbsent(enriched, envelope, "rptId");
        copyIfAbsent(enriched, envelope, "systemBytes");
        return enriched;
    }

    private String secsMessageName(Map<String, Object> request) {
        String direct = text(request, "secsMessage", "");
        if (!direct.isBlank()) {
            return direct;
        }
        String stream = text(request, "stream", "");
        String function = text(request, "function", "");
        return stream.isBlank() || function.isBlank() ? "" : "S" + stream + "F" + function;
    }
}
