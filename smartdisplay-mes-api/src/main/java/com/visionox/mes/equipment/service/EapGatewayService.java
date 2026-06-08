package com.visionox.mes.equipment.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.adapter.EapAdapter;
import com.visionox.mes.equipment.driver.EapProtocolDriver;
import com.visionox.mes.equipment.driver.EapProtocolDriverRegistry;
import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;
import com.visionox.mes.equipment.entity.EquipmentGatewayHealthCheck;
import com.visionox.mes.equipment.entity.EquipmentGatewayMessage;
import com.visionox.mes.equipment.mapper.EquipmentGatewayConnectionMapper;
import com.visionox.mes.equipment.mapper.EquipmentGatewayHealthCheckMapper;
import com.visionox.mes.equipment.mapper.EquipmentGatewayMessageMapper;
import com.visionox.mes.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EapGatewayService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final EquipmentGatewayConnectionMapper gatewayMapper;
    private final EquipmentGatewayMessageMapper messageMapper;
    private final EquipmentGatewayHealthCheckMapper healthCheckMapper;
    private final AuditLogService auditLogService;
    private final EapProtocolDriverRegistry protocolDriverRegistry;

    public List<Map<String, Object>> gateways(String status) {
        LambdaQueryWrapper<EquipmentGatewayConnection> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(EquipmentGatewayConnection::getStatus, status.toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(EquipmentGatewayConnection::getUpdatedTime).last("LIMIT 50");
        List<EquipmentGatewayConnection> rows = gatewayMapper.selectList(wrapper);
        return (rows == null ? List.<EquipmentGatewayConnection>of() : rows).stream()
                .map(this::gatewayRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> messages(String gatewayCode) {
        LambdaQueryWrapper<EquipmentGatewayMessage> wrapper = new LambdaQueryWrapper<>();
        if (gatewayCode != null && !gatewayCode.isBlank()) {
            wrapper.eq(EquipmentGatewayMessage::getGatewayCode, gatewayCode);
        }
        wrapper.orderByDesc(EquipmentGatewayMessage::getOccurredTime).last("LIMIT 80");
        List<EquipmentGatewayMessage> rows = messageMapper.selectList(wrapper);
        return (rows == null ? List.<EquipmentGatewayMessage>of() : rows).stream()
                .map(this::messageRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> healthChecks(String gatewayCode) {
        LambdaQueryWrapper<EquipmentGatewayHealthCheck> wrapper = new LambdaQueryWrapper<>();
        if (gatewayCode != null && !gatewayCode.isBlank()) {
            wrapper.eq(EquipmentGatewayHealthCheck::getGatewayCode, gatewayCode);
        }
        wrapper.orderByDesc(EquipmentGatewayHealthCheck::getCheckedTime).last("LIMIT 80");
        List<EquipmentGatewayHealthCheck> rows = healthCheckMapper.selectList(wrapper);
        return (rows == null ? List.<EquipmentGatewayHealthCheck>of() : rows).stream()
                .map(this::healthCheckRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> drivers() {
        return protocolDriverRegistry.summaries();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> registerGateway(Map<String, Object> request) {
        Map<String, Object> safeRequest = safeRequest(request);
        LocalDateTime now = LocalDateTime.now();
        String gatewayCode = requiredText(safeRequest, "gatewayCode");
        EquipmentGatewayConnection gateway = gatewayMapper.selectOne(new LambdaQueryWrapper<EquipmentGatewayConnection>()
                .eq(EquipmentGatewayConnection::getGatewayCode, gatewayCode));
        boolean created = gateway == null;
        if (created) {
            gateway = new EquipmentGatewayConnection();
            gateway.setGatewayCode(gatewayCode);
            gateway.setCreatedBy(text(safeRequest, "operator", currentUser()));
            gateway.setCreatedTime(now);
        }
        gateway.setGatewayName(requiredText(safeRequest, "gatewayName"));
        gateway.setProtocolType(normalizeProtocol(requiredText(safeRequest, "protocolType")));
        EapProtocolDriver driver = protocolDriverRegistry.resolve(gateway.getProtocolType());
        gateway.setDriverCode(text(safeRequest, "driverCode", driver.driverCode()));
        gateway.setDriverMode(text(safeRequest, "driverMode", "SIMULATED").toUpperCase(Locale.ROOT));
        gateway.setEndpointUri(requiredText(safeRequest, "endpointUri"));
        gateway.setLineCode(text(safeRequest, "lineCode", "LINE_01"));
        gateway.setEquipmentCodes(equipmentCodesJson(value(safeRequest, "equipmentCodes")));
        gateway.setStatus(text(safeRequest, "status", created ? "DISCONNECTED" : gateway.getStatus()).toUpperCase(Locale.ROOT));
        gateway.setHeartbeatIntervalMs(Math.max(100, intValue(value(safeRequest, "heartbeatIntervalMs"), 1000, "heartbeatIntervalMs")));
        gateway.setTlsEnabled(booleanFlag(value(safeRequest, "tlsEnabled"), 0));
        gateway.setConnectionTimeoutMs(Math.max(100, intValue(value(safeRequest, "connectionTimeoutMs"), 3000, "connectionTimeoutMs")));
        gateway.setReadTimeoutMs(Math.max(100, intValue(value(safeRequest, "readTimeoutMs"), 5000, "readTimeoutMs")));
        gateway.setLastError(text(safeRequest, "lastError", ""));
        gateway.setEnabled(booleanFlag(value(safeRequest, "enabled"), 1));
        gateway.setUpdatedBy(text(safeRequest, "operator", currentUser()));
        gateway.setUpdatedTime(now);
        gateway.setDriverConfigSnapshot(driverConfigSnapshot(gateway, safeRequest, driver));
        gateway.setRequestSnapshot(JSONUtil.toJsonStr(safeRequest));
        if (created) {
            gatewayMapper.insert(gateway);
        } else {
            gatewayMapper.updateById(gateway);
        }
        audit("EQUIPMENT_GATEWAY_REGISTER", gateway.getGatewayCode(), "EQUIPMENT_GATEWAY",
                (created ? "created " : "updated ") + gateway.getProtocolType() + " gateway " + gateway.getEndpointUri(),
                gateway.getUpdatedBy(), gateway.getRequestSnapshot());
        return Map.of("gateway", gatewayRow(gateway), "created", created);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> heartbeat(String gatewayCode, Map<String, Object> request) {
        EquipmentGatewayConnection gateway = findGateway(gatewayCode);
        Map<String, Object> safeRequest = safeRequest(request);
        LocalDateTime now = LocalDateTime.now();
        gateway.setStatus(text(safeRequest, "status", "CONNECTED").toUpperCase(Locale.ROOT));
        gateway.setLastHeartbeatTime(now);
        gateway.setLastError(text(safeRequest, "lastError", ""));
        gateway.setUpdatedBy(text(safeRequest, "operator", currentUser()));
        gateway.setUpdatedTime(now);
        gateway.setRequestSnapshot(JSONUtil.toJsonStr(safeRequest));
        gatewayMapper.updateById(gateway);
        audit("EQUIPMENT_GATEWAY_HEARTBEAT", gateway.getGatewayCode(), "EQUIPMENT_GATEWAY",
                "gateway=" + gateway.getGatewayCode() + ", status=" + gateway.getStatus(),
                gateway.getUpdatedBy(), gateway.getRequestSnapshot());
        return Map.of("gateway", gatewayRow(gateway));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkHealth(String gatewayCode, Map<String, Object> request) {
        EquipmentGatewayConnection gateway = findGateway(gatewayCode);
        Map<String, Object> safeRequest = safeRequest(request);
        EapProtocolDriver driver = protocolDriverRegistry.resolve(gateway.getProtocolType());
        LocalDateTime now = LocalDateTime.now();
        String operator = text(safeRequest, "operator", currentUser());
        EquipmentGatewayHealthCheck check = new EquipmentGatewayHealthCheck();
        check.setCheckNo(text(safeRequest, "checkNo", nextNo("EGH")));
        check.setGatewayCode(gateway.getGatewayCode());
        check.setProtocolType(gateway.getProtocolType());
        check.setDriverCode(valueOr(gateway.getDriverCode(), driver.driverCode()));
        check.setEndpointUri(gateway.getEndpointUri());
        check.setCheckType(text(safeRequest, "checkType", "MANUAL").toUpperCase(Locale.ROOT));
        check.setResultStatus("CHECKING");
        check.setCheckedBy(operator);
        check.setCheckedTime(now);
        check.setRequestSnapshot(JSONUtil.toJsonStr(safeRequest));
        healthCheckMapper.insert(check);

        long started = System.nanoTime();
        Map<String, Object> driverResult;
        try {
            driverResult = driver.checkHealth(gateway, safeRequest);
        } catch (RuntimeException ex) {
            driverResult = new LinkedHashMap<>();
            driverResult.put("protocolType", gateway.getProtocolType());
            driverResult.put("driverCode", valueOr(gateway.getDriverCode(), driver.driverCode()));
            driverResult.put("endpointUri", gateway.getEndpointUri());
            driverResult.put("resultStatus", "FAIL");
            driverResult.put("message", truncate(ex.getMessage(), 500));
        }
        int latencyMs = Math.max(0, (int) ((System.nanoTime() - started) / 1_000_000));
        String resultStatus = normalizeHealthStatus(text(driverResult, "resultStatus", "WARN"));
        String message = truncate(text(driverResult, "message", ""), 500);

        check.setResultStatus(resultStatus);
        check.setLatencyMs(latencyMs);
        check.setErrorMessage("PASS".equals(resultStatus) ? "" : message);
        check.setResponseSnapshot(JSONUtil.toJsonStr(driverResult));
        healthCheckMapper.updateById(check);

        gateway.setStatus(gatewayStatusFromHealth(resultStatus));
        gateway.setLastError("PASS".equals(resultStatus) ? "" : message);
        gateway.setUpdatedBy(operator);
        gateway.setUpdatedTime(LocalDateTime.now());
        gatewayMapper.updateById(gateway);

        audit("EQUIPMENT_GATEWAY_HEALTH_CHECK", gateway.getGatewayCode(), "EQUIPMENT_GATEWAY",
                "gateway=" + gateway.getGatewayCode() + ", result=" + resultStatus,
                operator, check.getResponseSnapshot());
        return Map.of("check", healthCheckRow(check), "gateway", gatewayRow(gateway), "driverResult", driverResult);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> ingestMessage(Map<String, Object> request, EapAdapter adapter) {
        Map<String, Object> safeRequest = safeRequest(request);
        LocalDateTime now = LocalDateTime.now();
        String gatewayCode = text(safeRequest, "gatewayCode", text(safeRequest, "gatewayId", "GW-SIM-HTTP-01"));
        EquipmentGatewayConnection gateway = gatewayMapper.selectOne(new LambdaQueryWrapper<EquipmentGatewayConnection>()
                .eq(EquipmentGatewayConnection::getGatewayCode, gatewayCode));
        String protocolType = gateway == null
                ? text(safeRequest, "protocol", "SIMULATED_HTTP").toUpperCase(Locale.ROOT)
                : gateway.getProtocolType();
        EapProtocolDriver driver = protocolDriverRegistry.resolve(protocolType);
        EquipmentGatewayMessage message = new EquipmentGatewayMessage();
        message.setMessageNo(text(safeRequest, "messageNo", nextNo("EGM")));
        message.setGatewayCode(gatewayCode);
        message.setEquipmentCode(equipmentCode(safeRequest));
        message.setProtocolType(protocolType);
        message.setDriverCode(driver.driverCode());
        message.setDirection(text(safeRequest, "direction", "INBOUND").toUpperCase(Locale.ROOT));
        message.setMessageType(normalizeMessageType(text(safeRequest, "messageType", text(safeRequest, "type", ""))));
        message.setCorrelationId(text(safeRequest, "correlationId", ""));
        message.setProcessStatus("RECEIVED");
        message.setOccurredTime(now);
        message.setPayloadSnapshot(JSONUtil.toJsonStr(safeRequest));
        message.setCreatedTime(now);
        messageMapper.insert(message);

        if (gateway != null) {
            gateway.setStatus("CONNECTED");
            gateway.setLastHeartbeatTime(now);
            gateway.setLastError("");
            if (valueOr(gateway.getDriverCode(), "").isBlank()) {
                gateway.setDriverCode(driver.driverCode());
            }
            gateway.setUpdatedTime(now);
            gateway.setUpdatedBy(text(safeRequest, "operator", currentUser()));
            gatewayMapper.updateById(gateway);
        }

        try {
            Map<String, Object> normalizedRequest = driver.normalizeInbound(gateway, safeRequest);
            message.setEquipmentCode(equipmentCode(normalizedRequest));
            message.setMessageType(normalizeMessageType(text(normalizedRequest, "messageType", text(normalizedRequest, "type", ""))));
            message.setNormalizedPayloadSnapshot(JSONUtil.toJsonStr(normalizedRequest));
            Map<String, Object> response = adapter.handleMessage(normalizedRequest);
            message.setProcessStatus("PROCESSED");
            message.setProcessedTime(LocalDateTime.now());
            message.setResponseSnapshot(JSONUtil.toJsonStr(response));
            messageMapper.updateById(message);
            audit("EAP_GATEWAY_MESSAGE", message.getMessageNo(), "EQUIPMENT_GATEWAY_MESSAGE",
                    "gateway=" + gatewayCode + ", type=" + message.getMessageType() + ", equipment=" + valueOr(message.getEquipmentCode(), ""),
                    text(safeRequest, "operator", currentUser()), message.getPayloadSnapshot());
            return Map.of("message", messageRow(message), "adapterResponse", response);
        } catch (RuntimeException ex) {
            message.setProcessStatus("FAILED");
            message.setErrorMessage(truncate(ex.getMessage(), 500));
            message.setProcessedTime(LocalDateTime.now());
            messageMapper.updateById(message);
            if (gateway != null) {
                gateway.setStatus("DEGRADED");
                gateway.setLastError(truncate(ex.getMessage(), 500));
                gateway.setUpdatedTime(LocalDateTime.now());
                gatewayMapper.updateById(gateway);
            }
            throw ex;
        }
    }

    private EquipmentGatewayConnection findGateway(String gatewayCode) {
        EquipmentGatewayConnection gateway = gatewayMapper.selectOne(new LambdaQueryWrapper<EquipmentGatewayConnection>()
                .eq(EquipmentGatewayConnection::getGatewayCode, gatewayCode));
        if (gateway == null) {
            throw new BusinessException("Equipment gateway not found: " + gatewayCode);
        }
        return gateway;
    }

    private Map<String, Object> gatewayRow(EquipmentGatewayConnection gateway) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("gatewayCode", gateway.getGatewayCode());
        row.put("gatewayName", gateway.getGatewayName());
        row.put("protocolType", gateway.getProtocolType());
        row.put("driverCode", gateway.getDriverCode());
        row.put("driverMode", gateway.getDriverMode());
        row.put("endpointUri", gateway.getEndpointUri());
        row.put("lineCode", gateway.getLineCode());
        row.put("equipmentCodes", gateway.getEquipmentCodes());
        row.put("status", gateway.getStatus());
        row.put("heartbeatIntervalMs", gateway.getHeartbeatIntervalMs());
        row.put("tlsEnabled", gateway.getTlsEnabled());
        row.put("connectionTimeoutMs", gateway.getConnectionTimeoutMs());
        row.put("readTimeoutMs", gateway.getReadTimeoutMs());
        row.put("lastHeartbeatTime", gateway.getLastHeartbeatTime());
        row.put("lastError", gateway.getLastError());
        row.put("enabled", gateway.getEnabled());
        row.put("updatedBy", gateway.getUpdatedBy());
        row.put("updatedTime", gateway.getUpdatedTime());
        row.put("time", formatTime(gateway.getUpdatedTime()));
        row.put("type", statusType(gateway.getStatus()));
        return row;
    }

    private Map<String, Object> healthCheckRow(EquipmentGatewayHealthCheck check) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("checkNo", check.getCheckNo());
        row.put("gatewayCode", check.getGatewayCode());
        row.put("protocolType", check.getProtocolType());
        row.put("driverCode", check.getDriverCode());
        row.put("endpointUri", check.getEndpointUri());
        row.put("checkType", check.getCheckType());
        row.put("resultStatus", check.getResultStatus());
        row.put("latencyMs", check.getLatencyMs());
        row.put("errorMessage", check.getErrorMessage());
        row.put("checkedBy", check.getCheckedBy());
        row.put("checkedTime", check.getCheckedTime());
        row.put("time", formatTime(check.getCheckedTime()));
        row.put("type", statusType(check.getResultStatus()));
        return row;
    }

    private Map<String, Object> messageRow(EquipmentGatewayMessage message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("messageNo", message.getMessageNo());
        row.put("gatewayCode", message.getGatewayCode());
        row.put("equipmentCode", message.getEquipmentCode());
        row.put("protocolType", message.getProtocolType());
        row.put("driverCode", message.getDriverCode());
        row.put("direction", message.getDirection());
        row.put("messageType", message.getMessageType());
        row.put("correlationId", message.getCorrelationId());
        row.put("processStatus", message.getProcessStatus());
        row.put("errorMessage", message.getErrorMessage());
        row.put("occurredTime", message.getOccurredTime());
        row.put("processedTime", message.getProcessedTime());
        row.put("time", formatTime(message.getOccurredTime()));
        row.put("type", statusType(message.getProcessStatus()));
        return row;
    }

    private String normalizeProtocol(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "SECS", "SECSGEM", "SECS_GEM" -> "SECS_GEM";
            case "OPCUA", "OPC_UA" -> "OPC_UA";
            case "HTTP", "VENDOR_HTTP" -> "VENDOR_HTTP";
            case "SIM", "SIMULATED", "SIMULATED_HTTP" -> "SIMULATED_HTTP";
            default -> normalized;
        };
    }

    private String normalizeMessageType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return normalized.isBlank() ? "UNKNOWN" : normalized;
    }

    private String normalizeHealthStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "OK", "ONLINE", "SUCCESS", "PASS" -> "PASS";
            case "WARN", "WARNING", "DEGRADED", "PENDING" -> "WARN";
            case "FAIL", "FAILED", "ERROR", "DOWN", "DISCONNECTED" -> "FAIL";
            default -> normalized.isBlank() ? "WARN" : normalized;
        };
    }

    private String gatewayStatusFromHealth(String resultStatus) {
        return switch (normalizeHealthStatus(resultStatus)) {
            case "PASS" -> "CONNECTED";
            case "FAIL" -> "DISCONNECTED";
            default -> "DEGRADED";
        };
    }

    @SuppressWarnings("unchecked")
    private String equipmentCode(Map<String, Object> request) {
        Object direct = value(request, "equipmentCode");
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct);
        }
        Object payload = value(request, "payload");
        if (payload instanceof Map<?, ?> map) {
            Object nested = map.get("equipmentCode");
            return nested == null ? "" : String.valueOf(nested);
        }
        return "";
    }

    private String equipmentCodesJson(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "[]";
        }
        if (value instanceof Iterable<?> || value instanceof Map<?, ?>) {
            return JSONUtil.toJsonStr(value);
        }
        String text = String.valueOf(value).trim();
        if (text.startsWith("[") || text.startsWith("{")) {
            return text;
        }
        return JSONUtil.toJsonStr(List.of(text.split("[,;\\s]+")));
    }

    @SuppressWarnings("unchecked")
    private String driverConfigSnapshot(EquipmentGatewayConnection gateway, Map<String, Object> request,
                                        EapProtocolDriver driver) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("driverCode", gateway.getDriverCode());
        snapshot.put("driverMode", gateway.getDriverMode());
        snapshot.put("protocolType", gateway.getProtocolType());
        snapshot.put("endpointUri", gateway.getEndpointUri());
        snapshot.put("tlsEnabled", gateway.getTlsEnabled());
        snapshot.put("connectionTimeoutMs", gateway.getConnectionTimeoutMs());
        snapshot.put("readTimeoutMs", gateway.getReadTimeoutMs());
        snapshot.put("capabilities", driver.capabilities());
        Object extraConfig = value(request, "driverConfig");
        if (extraConfig instanceof Map<?, ?> map) {
            Map<String, Object> config = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (key != null) {
                    config.put(String.valueOf(key), item);
                }
            });
            snapshot.put("driverConfig", config);
        } else if (extraConfig != null && !String.valueOf(extraConfig).isBlank()) {
            snapshot.put("driverConfig", String.valueOf(extraConfig));
        }
        return JSONUtil.toJsonStr(snapshot);
    }

    private Integer booleanFlag(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue() == 0 ? 0 : 1;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return List.of("true", "1", "yes", "y", "enabled").contains(text) ? 1 : 0;
    }

    private int intValue(Object value, int defaultValue, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(field + " must be numeric");
        }
    }

    private void audit(String action, String bizNo, String bizType, String description, String operator, String snapshot) {
        try {
            auditLogService.record(action, bizNo, bizType, description, operator, "equipment-gateway-service", snapshot);
        } catch (Exception e) {
            log.warn("equipment gateway audit write failed: action={}, bizNo={}, reason={}", action, bizNo, e.getMessage());
        }
    }

    private String requiredText(Map<String, Object> request, String key) {
        String value = text(request, key, "");
        if (value.isBlank()) {
            throw new BusinessException(key + " is required");
        }
        return value;
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = value(request, key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private Object value(Map<String, Object> request, String key) {
        return request == null ? null : request.get(key);
    }

    private Map<String, Object> safeRequest(Map<String, Object> request) {
        return request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
    }

    private String currentUser() {
        return AuthContext.username();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String statusType(String status) {
        String value = valueOr(status, "").toUpperCase(Locale.ROOT);
        if (List.of("CONNECTED", "PROCESSED", "ACTIVE", "PASS").contains(value)) {
            return "green";
        }
        if (List.of("DEGRADED", "RECEIVED", "PROCESSING", "WARN", "CHECKING").contains(value)) {
            return "amber";
        }
        if (List.of("FAILED", "FAIL", "DISCONNECTED", "DOWN").contains(value)) {
            return "red";
        }
        return "blue";
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.toLocalTime().withNano(0).toString();
    }

    private String nextNo(String prefix) {
        int seq = Math.floorMod(SEQUENCE.incrementAndGet(), 10000);
        return prefix + "-" + NO_TIME.format(LocalDateTime.now()) + "-" + String.format("%04d", seq);
    }
}
