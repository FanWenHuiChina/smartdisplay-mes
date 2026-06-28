package com.visionox.mes.equipment.driver;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;

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
        return "SECS/GEM 影子协议驱动，负责校验 S/F 帧并将 CEID/RPTID 数据归一为 MES 标准消息。";
    }

    @Override
    protected boolean requiresRealEquipmentLink() {
        return true;
    }

    @Override
    public Map<String, Object> normalizeInbound(EquipmentGatewayConnection gateway, Map<String, Object> request) {
        Map<String, Object> envelope = safeMap(request);
        Map<String, Object> payload = payload(envelope);
        Map<String, Object> normalized = super.normalizeInbound(gateway, request);
        String streamFunction = secsMessageName(envelope, payload);
        if ("UNKNOWN".equals(normalized.get("messageType")) && !streamFunction.isBlank()) {
            normalized.put("messageType", normalizeMessageType(streamFunction));
        }
        return normalized;
    }

    @Override
    protected void validateInbound(EquipmentGatewayConnection gateway, Map<String, Object> envelope,
                                   Map<String, Object> payload) {
        String secsMessage = secsMessageName(envelope, payload);
        if (secsMessage.isBlank()) {
            throw new BusinessException("SECS/GEM frame is invalid: secsMessage or stream/function is required");
        }
        requireAnyText("SECS/GEM equipment identity is missing", envelope, payload, "equipmentCode");
    }

    @Override
    protected Map<String, Object> enrichPayload(Map<String, Object> payload, Map<String, Object> envelope) {
        Map<String, Object> enriched = super.enrichPayload(payload, envelope);
        String streamFunction = secsMessageName(envelope, payload);
        if (!streamFunction.isBlank()) {
            enriched.putIfAbsent("secsMessage", streamFunction);
        }
        copyIfAbsent(enriched, envelope, "stream");
        copyIfAbsent(enriched, envelope, "function");
        copyIfAbsent(enriched, envelope, "deviceId");
        copyIfAbsent(enriched, envelope, "ceid");
        copyIfAbsent(enriched, envelope, "rptId");
        copyIfAbsent(enriched, envelope, "systemBytes");
        copyIfAbsent(enriched, envelope, "transactionId");
        return enriched;
    }

    private String secsMessageName(Map<String, Object> envelope, Map<String, Object> payload) {
        String direct = text(envelope, "secsMessage", text(payload, "secsMessage", ""));
        if (!direct.isBlank()) {
            return direct.trim().toUpperCase();
        }
        String stream = text(envelope, "stream", text(payload, "stream", ""));
        String function = text(envelope, "function", text(payload, "function", ""));
        return stream.isBlank() || function.isBlank() ? "" : "S" + stream + "F" + function;
    }
}
