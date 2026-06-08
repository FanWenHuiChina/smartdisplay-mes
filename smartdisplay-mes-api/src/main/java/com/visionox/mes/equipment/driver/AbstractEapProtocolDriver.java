package com.visionox.mes.equipment.driver;

import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

abstract class AbstractEapProtocolDriver implements EapProtocolDriver {

    @Override
    public Map<String, Object> capabilities() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("protocolType", protocolType());
        row.put("driverCode", driverCode());
        row.put("driverMode", "SIMULATED");
        row.put("supportedMessageTypes", List.of("STATUS", "CYCLE", "PARAMETER", "RECIPE_DOWNLOAD"));
        row.put("description", description());
        row.put("requiresRealEquipmentLink", requiresRealEquipmentLink());
        return row;
    }

    @Override
    public Map<String, Object> normalizeInbound(EquipmentGatewayConnection gateway, Map<String, Object> request) {
        Map<String, Object> envelope = safeMap(request);
        Map<String, Object> payload = payload(envelope);
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("gatewayCode", gatewayCode(gateway, envelope));
        normalized.put("gatewayId", gatewayCode(gateway, envelope));
        normalized.put("protocol", protocolType());
        normalized.put("protocolType", protocolType());
        normalized.put("driverCode", driverCode());
        normalized.put("messageType", normalizeMessageType(text(envelope, "messageType", text(envelope, "type", ""))));
        normalized.put("correlationId", text(envelope, "correlationId", ""));
        normalized.put("operator", text(envelope, "operator", ""));
        normalized.put("sourceSystem", text(envelope, "sourceSystem", "eap-gateway"));
        normalized.put("payload", enrichPayload(payload, envelope));
        return normalized;
    }

    @Override
    public Map<String, Object> checkHealth(EquipmentGatewayConnection gateway, Map<String, Object> request) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("protocolType", protocolType());
        row.put("driverCode", driverCode());
        row.put("driverMode", gateway == null ? "UNKNOWN" : textValue(gateway.getDriverMode(), "SIMULATED"));
        row.put("endpointUri", gateway == null ? "" : textValue(gateway.getEndpointUri(), ""));
        row.put("requiresRealEquipmentLink", requiresRealEquipmentLink());
        row.put("checkedAt", LocalDateTime.now().toString());

        if (gateway == null) {
            row.put("resultStatus", "FAIL");
            row.put("message", "gateway not found");
            return row;
        }
        if (gateway.getEnabled() != null && gateway.getEnabled() == 0) {
            row.put("resultStatus", "FAIL");
            row.put("message", "gateway disabled");
            return row;
        }
        if (textValue(gateway.getEndpointUri(), "").isBlank()) {
            row.put("resultStatus", "FAIL");
            row.put("message", "endpoint uri is empty");
            return row;
        }
        if (requiresRealEquipmentLink()) {
            row.put("resultStatus", "WARN");
            row.put("message", protocolType() + " protocol boundary configured; real equipment handshake pending");
            return row;
        }
        row.put("resultStatus", "PASS");
        row.put("message", "simulated gateway reachable");
        return row;
    }

    protected abstract String description();

    protected boolean requiresRealEquipmentLink() {
        return false;
    }

    protected Map<String, Object> enrichPayload(Map<String, Object> payload, Map<String, Object> envelope) {
        return new LinkedHashMap<>(payload);
    }

    protected String normalizeMessageType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "EQUIPMENT_STATUS", "STATUS_REPORT", "S6F11", "ALARM_REPORT", "DATA_CHANGE" -> "STATUS";
            case "CYCLE_SAMPLE", "CYCLE_REPORT", "UNIT_COMPLETE" -> "CYCLE";
            case "PARAMETER_SAMPLE", "PARAMETER_REPORT", "TRACE_DATA" -> "PARAMETER";
            case "RECIPE", "DOWNLOAD", "PP_SELECT", "S2F41" -> "RECIPE_DOWNLOAD";
            default -> normalized.isBlank() ? "UNKNOWN" : normalized;
        };
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> payload(Map<String, Object> envelope) {
        Object value = envelope.get("payload");
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> payload = new LinkedHashMap<>();
            mapValue.forEach((key, item) -> {
                if (key != null) {
                    payload.put(String.valueOf(key), item);
                }
            });
            return payload;
        }
        Map<String, Object> flatPayload = new LinkedHashMap<>(envelope);
        flatPayload.remove("gatewayCode");
        flatPayload.remove("gatewayId");
        flatPayload.remove("messageType");
        flatPayload.remove("type");
        flatPayload.remove("correlationId");
        flatPayload.remove("operator");
        flatPayload.remove("sourceSystem");
        return flatPayload;
    }

    protected void copyIfAbsent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!target.containsKey(key) && value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    protected String text(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String textValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String gatewayCode(EquipmentGatewayConnection gateway, Map<String, Object> envelope) {
        if (gateway != null && gateway.getGatewayCode() != null && !gateway.getGatewayCode().isBlank()) {
            return gateway.getGatewayCode();
        }
        return text(envelope, "gatewayCode", text(envelope, "gatewayId", ""));
    }

    private Map<String, Object> safeMap(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
    }
}
