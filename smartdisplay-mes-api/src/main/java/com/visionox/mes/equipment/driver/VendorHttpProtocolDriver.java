package com.visionox.mes.equipment.driver;

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
        return "厂商 HTTP 驱动占位，负责将设备厂商 HTTP JSON 归一为 MES 标准消息。";
    }

    @Override
    protected boolean requiresRealEquipmentLink() {
        return true;
    }

    @Override
    protected Map<String, Object> enrichPayload(Map<String, Object> payload, Map<String, Object> envelope) {
        Map<String, Object> enriched = super.enrichPayload(payload, envelope);
        copyIfAbsent(enriched, envelope, "httpMethod");
        copyIfAbsent(enriched, envelope, "signature");
        copyIfAbsent(enriched, envelope, "vendorMessageId");
        return enriched;
    }
}
