package com.visionox.mes.equipment.adapter;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 试点默认 EAP 适配器：接收标准化消息并调用设备领域服务。
 */
@Service
@RequiredArgsConstructor
public class SimulatedEapAdapter implements EapAdapter {

    private static final String DEFAULT_SOURCE = "eap-adapter";
    private static final String DEFAULT_PROTOCOL = "SIMULATED_HTTP";

    private final EquipmentService equipmentService;

    @Override
    public String adapterCode() {
        return "simulated-eap-adapter";
    }

    @Override
    public Map<String, Object> handleMessage(Map<String, Object> request) {
        Map<String, Object> envelope = safeRequest(request);
        String messageType = normalizeMessageType(text(envelope, "messageType", text(envelope, "type", "")));
        Map<String, Object> payload = payload(envelope);
        payload = enrich(payload, envelope);

        Map<String, Object> result = switch (messageType) {
            case "STATUS" -> reportStatus(payload);
            case "CYCLE" -> reportCycleSample(payload);
            case "PARAMETER" -> reportParameters(payload);
            case "RECIPE_DOWNLOAD" -> downloadRecipe(payload);
            default -> throw new BusinessException("Unsupported EAP messageType: " + messageType);
        };

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("adapterCode", adapterCode());
        response.put("protocol", text(payload, "protocol", DEFAULT_PROTOCOL));
        response.put("messageType", messageType);
        response.put("correlationId", text(payload, "correlationId", ""));
        response.put("result", result);
        return response;
    }

    @Override
    public Map<String, Object> reportStatus(Map<String, Object> request) {
        return equipmentService.reportStatus(enrich(safeRequest(request), Map.of()));
    }

    @Override
    public Map<String, Object> reportCycleSample(Map<String, Object> request) {
        return equipmentService.reportCycleSample(enrich(safeRequest(request), Map.of()));
    }

    @Override
    public Map<String, Object> reportParameters(Map<String, Object> request) {
        return equipmentService.reportParameters(enrich(safeRequest(request), Map.of()));
    }

    @Override
    public Map<String, Object> downloadRecipe(Map<String, Object> request) {
        return equipmentService.downloadRecipe(enrich(safeRequest(request), Map.of()));
    }

    private Map<String, Object> enrich(Map<String, Object> payload, Map<String, Object> envelope) {
        Map<String, Object> enriched = new LinkedHashMap<>(payload);
        copyIfAbsent(enriched, envelope, "operator");
        copyIfAbsent(enriched, envelope, "correlationId");
        copyIfAbsent(enriched, envelope, "gatewayId");
        copyIfAbsent(enriched, envelope, "protocol");
        enriched.putIfAbsent("sourceSystem", text(envelope, "sourceSystem", DEFAULT_SOURCE));
        enriched.putIfAbsent("protocol", DEFAULT_PROTOCOL);
        enriched.put("adapterCode", adapterCode());
        return enriched;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> envelope) {
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
        flatPayload.remove("messageType");
        flatPayload.remove("type");
        return flatPayload;
    }

    private String normalizeMessageType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "STATUS", "EQUIPMENT_STATUS", "STATUS_REPORT" -> "STATUS";
            case "CYCLE", "CYCLE_SAMPLE", "CYCLE_REPORT" -> "CYCLE";
            case "PARAMETER", "PARAMETER_SAMPLE", "PARAMETER_REPORT" -> "PARAMETER";
            case "RECIPE", "DOWNLOAD", "RECIPE_DOWNLOAD" -> "RECIPE_DOWNLOAD";
            default -> normalized;
        };
    }

    private void copyIfAbsent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (!target.containsKey(key) && source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private Map<String, Object> safeRequest(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
    }

    private String text(Map<String, Object> source, String key, String fallback) {
        Object value = source.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }
}
