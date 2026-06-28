package com.visionox.mes.equipment.driver;

import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;
import com.visionox.mes.common.BusinessException;

import java.time.LocalDateTime;
import java.util.Arrays;
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
        row.put("driverMode", defaultDriverMode());
        row.put("supportedDriverModes", requiresRealEquipmentLink()
                ? List.of("SIMULATED", "SHADOW", "EXTERNAL")
                : List.of("SIMULATED"));
        row.put("supportedMessageTypes", List.of("STATUS", "CYCLE", "PARAMETER", "RECIPE_DOWNLOAD"));
        row.put("protocolFrameValidation", true);
        row.put("description", description());
        row.put("requiresRealEquipmentLink", requiresRealEquipmentLink());
        return row;
    }

    @Override
    public Map<String, Object> normalizeInbound(EquipmentGatewayConnection gateway, Map<String, Object> request) {
        Map<String, Object> envelope = safeMap(request);
        Map<String, Object> payload = payload(envelope);
        validateInbound(gateway, envelope, payload);
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
        String driverMode = normalizeDriverMode(textValue(gateway.getDriverMode(), "SIMULATED"));
        row.put("driverMode", driverMode);
        if (!requiresRealEquipmentLink()) {
            row.put("resultStatus", "PASS");
            row.put("message", "simulated gateway reachable");
            return row;
        }
        if ("SIMULATED".equals(driverMode)) {
            row.put("resultStatus", "PASS");
            row.put("message", protocolType() + " simulated protocol gateway reachable; frame validation active");
            return row;
        }
        if ("SHADOW".equals(driverMode)) {
            row.put("resultStatus", "WARN");
            row.put("message", protocolType() + " shadow protocol frame validation ready; real equipment handshake not configured");
            return row;
        }
        if ("EXTERNAL".equals(driverMode) && !truthy(value(request, "realLinkConfigured"))) {
            row.put("resultStatus", "FAIL");
            row.put("message", protocolType() + " external equipment link is not configured in pilot mode");
            return row;
        }
        row.put("resultStatus", "WARN");
        row.put("message", protocolType() + " external endpoint configured; live equipment handshake must be verified by site EAP");
        return row;
    }

    protected abstract String description();

    protected boolean requiresRealEquipmentLink() {
        return false;
    }

    protected String defaultDriverMode() {
        return requiresRealEquipmentLink() ? "SHADOW" : "SIMULATED";
    }

    protected void validateInbound(EquipmentGatewayConnection gateway, Map<String, Object> envelope,
                                   Map<String, Object> payload) {
        // 默认模拟 HTTP 仅做公共信封归一化，真实协议驱动在子类补充协议帧校验。
    }

    protected Map<String, Object> enrichPayload(Map<String, Object> payload, Map<String, Object> envelope) {
        return new LinkedHashMap<>(payload);
    }

    protected String normalizeMessageType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "EQUIPMENT_STATUS", "STATUS_REPORT", "S6F11", "ALARM_REPORT" -> "STATUS";
            case "CYCLE_SAMPLE", "CYCLE_REPORT", "UNIT_COMPLETE", "S6F12" -> "CYCLE";
            case "PARAMETER_SAMPLE", "PARAMETER_REPORT", "TRACE_DATA", "DATA_CHANGE", "S6F1", "S6F3" -> "PARAMETER";
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

    protected void requireAnyText(String message, Map<String, Object> envelope, Map<String, Object> payload,
                                  String... keys) {
        boolean present = Arrays.stream(keys).anyMatch(key -> hasText(envelope, key) || hasText(payload, key));
        if (!present) {
            throw new BusinessException(message + ": require one of " + String.join(", ", keys));
        }
    }

    protected boolean hasText(Map<String, Object> source, String key) {
        if (source == null) {
            return false;
        }
        Object value = source.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }

    protected String text(Map<String, Object> source, String key, String fallback) {
        if (source == null) {
            return fallback;
        }
        Object value = source.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    protected Object value(Map<String, Object> source, String key) {
        return source == null ? null : source.get(key);
    }

    protected Map<String, Object> safeMap(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
    }

    private String textValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String normalizeDriverMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "REAL", "LIVE", "EXTERNAL" -> "EXTERNAL";
            case "SHADOW", "DRY_RUN" -> "SHADOW";
            case "SIM", "SIMULATED" -> "SIMULATED";
            default -> normalized.isBlank() ? defaultDriverMode() : normalized;
        };
    }

    private boolean truthy(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return List.of("true", "1", "yes", "y", "enabled", "configured").contains(text);
    }

    private String gatewayCode(EquipmentGatewayConnection gateway, Map<String, Object> envelope) {
        if (gateway != null && gateway.getGatewayCode() != null && !gateway.getGatewayCode().isBlank()) {
            return gateway.getGatewayCode();
        }
        return text(envelope, "gatewayCode", text(envelope, "gatewayId", ""));
    }

}
