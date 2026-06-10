package com.visionox.mes.equipment.driver;

import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;

import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class VendorHttpProtocolDriver extends AbstractEapProtocolDriver {

    @Override
    public String protocolType() {
        return "VENDOR_HTTP";
    }

    @Override
    public String driverCode() {
        return "vendor-http-driver";
    }

    @Override
    protected String description() {
        return "厂商 HTTP 影子协议驱动，负责校验厂商消息标识并将 HTTP JSON 归一为 MES 标准消息。";
    }

    @Override
    protected boolean requiresRealEquipmentLink() {
        return true;
    }

    @Override
    public Map<String, Object> normalizeInbound(EquipmentGatewayConnection gateway, Map<String, Object> request) {
        Map<String, Object> envelope = safeMap(request);
        Map<String, Object> normalized = super.normalizeInbound(gateway, request);
        String vendorType = text(envelope, "vendorMessageType", text(envelope, "eventType", ""));
        if ("UNKNOWN".equals(normalized.get("messageType")) && !vendorType.isBlank()) {
            normalized.put("messageType", normalizeMessageType(vendorType));
        }
        return normalized;
    }

    @Override
    protected void validateInbound(EquipmentGatewayConnection gateway, Map<String, Object> envelope,
                                   Map<String, Object> payload) {
        requireAnyText("Vendor HTTP message identity is missing", envelope, payload,
                "vendorMessageId", "signature", "correlationId", "requestId");
        requireAnyText("Vendor HTTP equipment identity is missing", envelope, payload, "equipmentCode");
    }

    @Override
    protected Map<String, Object> enrichPayload(Map<String, Object> payload, Map<String, Object> envelope) {
        Map<String, Object> enriched = super.enrichPayload(payload, envelope);
        enriched.putIfAbsent("httpMethod", text(envelope, "httpMethod", "POST").toUpperCase());
        copyIfAbsent(enriched, envelope, "httpMethod");
        copyIfAbsent(enriched, envelope, "requestPath");
        copyIfAbsent(enriched, envelope, "requestId");
        copyIfAbsent(enriched, envelope, "signature");
        copyIfAbsent(enriched, envelope, "vendorMessageId");
        copyIfAbsent(enriched, envelope, "vendorMessageType");
        copyIfAbsent(enriched, envelope, "vendorCode");
        copyIfAbsent(enriched, envelope, "headers");
        return enriched;
    }
}
