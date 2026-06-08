package com.visionox.mes.equipment.driver;

import com.visionox.mes.common.BusinessException;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EapProtocolDriverRegistry {

    private final Map<String, EapProtocolDriver> drivers;

    public EapProtocolDriverRegistry(List<EapProtocolDriver> drivers) {
        this.drivers = drivers.stream()
                .collect(Collectors.toMap(driver -> normalizeProtocol(driver.protocolType()), driver -> driver,
                        (left, right) -> left, LinkedHashMap::new));
    }

    public EapProtocolDriver resolve(String protocolType) {
        String normalized = normalizeProtocol(protocolType);
        EapProtocolDriver driver = drivers.get(normalized);
        if (driver == null) {
            throw new BusinessException("Unsupported EAP protocolType: " + protocolType);
        }
        return driver;
    }

    public List<Map<String, Object>> summaries() {
        return drivers.values().stream()
                .sorted(Comparator.comparing(EapProtocolDriver::protocolType))
                .map(EapProtocolDriver::capabilities)
                .collect(Collectors.toList());
    }

    private String normalizeProtocol(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "SECS", "SECSGEM", "SECS_GEM" -> "SECS_GEM";
            case "OPCUA", "OPC_UA" -> "OPC_UA";
            case "HTTP", "VENDOR_HTTP" -> "VENDOR_HTTP";
            case "SIM", "SIMULATED", "SIMULATED_HTTP", "" -> "SIMULATED_HTTP";
            default -> normalized;
        };
    }
}
