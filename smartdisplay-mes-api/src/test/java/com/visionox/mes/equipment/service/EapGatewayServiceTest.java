package com.visionox.mes.equipment.service;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.adapter.EapAdapter;
import com.visionox.mes.equipment.driver.EapProtocolDriver;
import com.visionox.mes.equipment.driver.EapProtocolDriverRegistry;
import com.visionox.mes.equipment.driver.OpcUaProtocolDriver;
import com.visionox.mes.equipment.driver.SecsGemProtocolDriver;
import com.visionox.mes.equipment.driver.SimulatedHttpProtocolDriver;
import com.visionox.mes.equipment.driver.VendorHttpProtocolDriver;
import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;
import com.visionox.mes.equipment.entity.EquipmentGatewayHealthCheck;
import com.visionox.mes.equipment.entity.EquipmentGatewayMessage;
import com.visionox.mes.equipment.mapper.EquipmentGatewayConnectionMapper;
import com.visionox.mes.equipment.mapper.EquipmentGatewayHealthCheckMapper;
import com.visionox.mes.equipment.mapper.EquipmentGatewayMessageMapper;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class EapGatewayServiceTest {

    @Mock
    private EquipmentGatewayConnectionMapper gatewayMapper;

    @Mock
    private EquipmentGatewayMessageMapper messageMapper;

    @Mock
    private EquipmentGatewayHealthCheckMapper healthCheckMapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private EapAdapter eapAdapter;

    @Test
    void registerGatewayShouldPersistNewGatewayAndAudit() {
        EapGatewayService service = service();
        when(gatewayMapper.selectOne(any())).thenReturn(null);

        service.registerGateway(Map.of(
                "gatewayCode", "GW-HTTP-01",
                "gatewayName", "厂商HTTP网关",
                "protocolType", "vendor-http",
                "endpointUri", "https://eap.example.local/messages",
                "equipmentCodes", "COATER_01 COATER_02",
                "driverMode", "simulated",
                "tlsEnabled", true,
                "connectionTimeoutMs", 5000,
                "readTimeoutMs", 8000,
                "operator", "ee1001"
        ));

        ArgumentCaptor<EquipmentGatewayConnection> captor = ArgumentCaptor.forClass(EquipmentGatewayConnection.class);
        verify(gatewayMapper).insert(captor.capture());
        EquipmentGatewayConnection gateway = captor.getValue();
        assertThat(gateway.getGatewayCode()).isEqualTo("GW-HTTP-01");
        assertThat(gateway.getProtocolType()).isEqualTo("VENDOR_HTTP");
        assertThat(gateway.getDriverCode()).isEqualTo("vendor-http-driver");
        assertThat(gateway.getDriverMode()).isEqualTo("SIMULATED");
        assertThat(gateway.getTlsEnabled()).isEqualTo(1);
        assertThat(gateway.getConnectionTimeoutMs()).isEqualTo(5000);
        assertThat(gateway.getReadTimeoutMs()).isEqualTo(8000);
        assertThat(gateway.getDriverConfigSnapshot()).contains("vendor-http-driver");
        assertThat(gateway.getStatus()).isEqualTo("DISCONNECTED");
        assertThat(gateway.getEquipmentCodes()).contains("COATER_01");
        verify(auditLogService).record(eq("EQUIPMENT_GATEWAY_REGISTER"), eq("GW-HTTP-01"), eq("EQUIPMENT_GATEWAY"), any(), eq("ee1001"), eq("equipment-gateway-service"), any());
    }

    @Test
    void heartbeatShouldUpdateGatewayStatusAndAudit() {
        EapGatewayService service = service();
        EquipmentGatewayConnection gateway = gateway("GW-SIM-HTTP-01", "DISCONNECTED");
        when(gatewayMapper.selectOne(any())).thenReturn(gateway);

        service.heartbeat("GW-SIM-HTTP-01", Map.of(
                "status", "CONNECTED",
                "operator", "ee1001"
        ));

        assertThat(gateway.getStatus()).isEqualTo("CONNECTED");
        assertThat(gateway.getLastHeartbeatTime()).isNotNull();
        verify(gatewayMapper).updateById(gateway);
        verify(auditLogService).record(eq("EQUIPMENT_GATEWAY_HEARTBEAT"), eq("GW-SIM-HTTP-01"), eq("EQUIPMENT_GATEWAY"), any(), eq("ee1001"), eq("equipment-gateway-service"), any());
    }

    @Test
    void checkHealthShouldPersistPassResultAndUpdateGateway() {
        EapGatewayService service = service();
        EquipmentGatewayConnection gateway = gateway("GW-SIM-HTTP-01", "DISCONNECTED");
        when(gatewayMapper.selectOne(any())).thenReturn(gateway);

        Map<String, Object> response = service.checkHealth("GW-SIM-HTTP-01", Map.of(
                "checkType", "MANUAL",
                "operator", "ee1001"
        ));

        ArgumentCaptor<EquipmentGatewayHealthCheck> captor = ArgumentCaptor.forClass(EquipmentGatewayHealthCheck.class);
        verify(healthCheckMapper).insert(captor.capture());
        EquipmentGatewayHealthCheck check = captor.getValue();
        assertThat(check.getGatewayCode()).isEqualTo("GW-SIM-HTTP-01");
        assertThat(check.getProtocolType()).isEqualTo("SIMULATED_HTTP");
        assertThat(check.getDriverCode()).isEqualTo("simulated-http-driver");
        assertThat(check.getResultStatus()).isEqualTo("PASS");
        assertThat(check.getResponseSnapshot()).contains("simulated gateway reachable");
        assertThat(gateway.getStatus()).isEqualTo("CONNECTED");
        assertThat(response.get("driverResult")).asString().contains("PASS");
        verify(healthCheckMapper).updateById(check);
        verify(gatewayMapper).updateById(gateway);
        verify(auditLogService).record(eq("EQUIPMENT_GATEWAY_HEALTH_CHECK"), eq("GW-SIM-HTTP-01"), eq("EQUIPMENT_GATEWAY"), any(), eq("ee1001"), eq("equipment-gateway-service"), any());
    }

    @Test
    void checkHealthShouldWarnForPlaceholderRealProtocol() {
        EapGatewayService service = service();
        EquipmentGatewayConnection gateway = gateway("GW-SECS-01", "DISCONNECTED");
        gateway.setProtocolType("SECS_GEM");
        gateway.setDriverCode("secs-gem-driver");
        when(gatewayMapper.selectOne(any())).thenReturn(gateway);

        service.checkHealth("GW-SECS-01", Map.of("operator", "ee1001"));

        ArgumentCaptor<EquipmentGatewayHealthCheck> captor = ArgumentCaptor.forClass(EquipmentGatewayHealthCheck.class);
        verify(healthCheckMapper).insert(captor.capture());
        EquipmentGatewayHealthCheck check = captor.getValue();
        assertThat(check.getResultStatus()).isEqualTo("WARN");
        assertThat(check.getErrorMessage()).contains("real equipment handshake pending");
        assertThat(gateway.getStatus()).isEqualTo("DEGRADED");
        verify(healthCheckMapper).updateById(check);
    }

    @Test
    void checkHealthShouldPersistFailResultWhenDriverThrows() {
        EapProtocolDriver failingDriver = new EapProtocolDriver() {
            @Override
            public String protocolType() {
                return "SIMULATED_HTTP";
            }

            @Override
            public String driverCode() {
                return "failing-driver";
            }

            @Override
            public Map<String, Object> capabilities() {
                return Map.of("protocolType", protocolType(), "driverCode", driverCode());
            }

            @Override
            public Map<String, Object> normalizeInbound(EquipmentGatewayConnection gateway, Map<String, Object> request) {
                return request;
            }

            @Override
            public Map<String, Object> checkHealth(EquipmentGatewayConnection gateway, Map<String, Object> request) {
                throw new BusinessException("socket timeout");
            }
        };
        EapGatewayService service = new EapGatewayService(gatewayMapper, messageMapper, healthCheckMapper,
                auditLogService, new EapProtocolDriverRegistry(List.of(failingDriver)));
        EquipmentGatewayConnection gateway = gateway("GW-SIM-HTTP-01", "CONNECTED");
        gateway.setDriverCode("failing-driver");
        when(gatewayMapper.selectOne(any())).thenReturn(gateway);

        service.checkHealth("GW-SIM-HTTP-01", Map.of("operator", "ee1001"));

        ArgumentCaptor<EquipmentGatewayHealthCheck> captor = ArgumentCaptor.forClass(EquipmentGatewayHealthCheck.class);
        verify(healthCheckMapper).insert(captor.capture());
        EquipmentGatewayHealthCheck check = captor.getValue();
        assertThat(check.getResultStatus()).isEqualTo("FAIL");
        assertThat(check.getErrorMessage()).contains("socket timeout");
        assertThat(gateway.getStatus()).isEqualTo("DISCONNECTED");
        verify(healthCheckMapper).updateById(check);
        verify(auditLogService).record(eq("EQUIPMENT_GATEWAY_HEALTH_CHECK"), eq("GW-SIM-HTTP-01"), eq("EQUIPMENT_GATEWAY"), any(), eq("ee1001"), eq("equipment-gateway-service"), any());
    }

    @Test
    void ingestMessageShouldRecordProcessedMessageAndUpdateGateway() {
        EapGatewayService service = service();
        EquipmentGatewayConnection gateway = gateway("GW-SIM-HTTP-01", "DISCONNECTED");
        when(gatewayMapper.selectOne(any())).thenReturn(gateway);
        when(eapAdapter.handleMessage(any())).thenReturn(Map.of("adapterCode", "simulated-eap-adapter", "messageType", "STATUS"));

        service.ingestMessage(Map.of(
                "gatewayCode", "GW-SIM-HTTP-01",
                "messageType", "STATUS",
                "correlationId", "MSG-001",
                "payload", Map.of("equipmentCode", "COATER_01", "status", "RUNNING"),
                "operator", "ee1001"
        ), eapAdapter);

        ArgumentCaptor<EquipmentGatewayMessage> captor = ArgumentCaptor.forClass(EquipmentGatewayMessage.class);
        verify(messageMapper).insert(captor.capture());
        EquipmentGatewayMessage message = captor.getValue();
        assertThat(message.getGatewayCode()).isEqualTo("GW-SIM-HTTP-01");
        assertThat(message.getEquipmentCode()).isEqualTo("COATER_01");
        assertThat(message.getDriverCode()).isEqualTo("simulated-http-driver");
        assertThat(message.getNormalizedPayloadSnapshot()).contains("simulated-http-driver");
        assertThat(message.getProcessStatus()).isEqualTo("PROCESSED");
        assertThat(message.getResponseSnapshot()).contains("simulated-eap-adapter");
        assertThat(gateway.getStatus()).isEqualTo("CONNECTED");
        verify(messageMapper).updateById(message);
        verify(auditLogService).record(eq("EAP_GATEWAY_MESSAGE"), eq(message.getMessageNo()), eq("EQUIPMENT_GATEWAY_MESSAGE"), any(), eq("ee1001"), eq("equipment-gateway-service"), any());
    }

    @Test
    void ingestMessageShouldMarkMessageFailedAndDegradeGatewayWhenAdapterFails() {
        EapGatewayService service = service();
        EquipmentGatewayConnection gateway = gateway("GW-SIM-HTTP-01", "CONNECTED");
        when(gatewayMapper.selectOne(any())).thenReturn(gateway);
        when(eapAdapter.handleMessage(any())).thenThrow(new BusinessException("Unsupported message"));

        assertThatThrownBy(() -> service.ingestMessage(Map.of(
                "gatewayCode", "GW-SIM-HTTP-01",
                "messageType", "UNKNOWN",
                "payload", Map.of("equipmentCode", "COATER_01"),
                "operator", "ee1001"
        ), eapAdapter)).isInstanceOf(BusinessException.class);

        ArgumentCaptor<EquipmentGatewayMessage> captor = ArgumentCaptor.forClass(EquipmentGatewayMessage.class);
        verify(messageMapper).insert(captor.capture());
        EquipmentGatewayMessage message = captor.getValue();
        assertThat(message.getProcessStatus()).isEqualTo("FAILED");
        assertThat(message.getErrorMessage()).contains("Unsupported message");
        assertThat(gateway.getStatus()).isEqualTo("DEGRADED");
        assertThat(gateway.getLastError()).contains("Unsupported message");
        verify(messageMapper).updateById(message);
        verify(gatewayMapper, times(2)).updateById(gateway);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestSecsGemMessageShouldNormalizeProtocolFrameBeforeAdapter() {
        EapGatewayService service = service();
        EquipmentGatewayConnection gateway = gateway("GW-SECS-01", "CONNECTED");
        gateway.setProtocolType("SECS_GEM");
        gateway.setDriverCode("secs-gem-driver");
        when(gatewayMapper.selectOne(any())).thenReturn(gateway);
        when(eapAdapter.handleMessage(any())).thenReturn(Map.of("adapterCode", "simulated-eap-adapter", "messageType", "STATUS"));

        service.ingestMessage(Map.of(
                "gatewayCode", "GW-SECS-01",
                "stream", "6",
                "function", "11",
                "correlationId", "SB-001",
                "payload", Map.of("equipmentCode", "EVAP_01", "status", "RUNNING"),
                "operator", "ee1001"
        ), eapAdapter);

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eapAdapter).handleMessage(requestCaptor.capture());
        Map<String, Object> normalized = requestCaptor.getValue();
        assertThat(normalized.get("protocolType")).isEqualTo("SECS_GEM");
        assertThat(normalized.get("driverCode")).isEqualTo("secs-gem-driver");
        assertThat(normalized.get("messageType")).isEqualTo("STATUS");
        assertThat((Map<String, Object>) normalized.get("payload")).containsEntry("stream", "6");

        ArgumentCaptor<EquipmentGatewayMessage> messageCaptor = ArgumentCaptor.forClass(EquipmentGatewayMessage.class);
        verify(messageMapper).insert(messageCaptor.capture());
        EquipmentGatewayMessage message = messageCaptor.getValue();
        assertThat(message.getDriverCode()).isEqualTo("secs-gem-driver");
        assertThat(message.getNormalizedPayloadSnapshot()).contains("SECS_GEM");
    }

    @Test
    void driversShouldExposeProtocolCapabilities() {
        EapGatewayService service = service();

        List<Map<String, Object>> drivers = service.drivers();

        assertThat(drivers).extracting(row -> row.get("protocolType"))
                .contains("SIMULATED_HTTP", "SECS_GEM", "OPC_UA", "VENDOR_HTTP");
    }

    private EquipmentGatewayConnection gateway(String gatewayCode, String status) {
        EquipmentGatewayConnection gateway = new EquipmentGatewayConnection();
        gateway.setGatewayCode(gatewayCode);
        gateway.setGatewayName(gatewayCode);
        gateway.setProtocolType("SIMULATED_HTTP");
        gateway.setDriverCode("simulated-http-driver");
        gateway.setDriverMode("SIMULATED");
        gateway.setEndpointUri("http://localhost/eap");
        gateway.setLineCode("LINE_01");
        gateway.setEquipmentCodes("[\"COATER_01\"]");
        gateway.setStatus(status);
        gateway.setHeartbeatIntervalMs(1000);
        gateway.setTlsEnabled(0);
        gateway.setConnectionTimeoutMs(3000);
        gateway.setReadTimeoutMs(5000);
        gateway.setEnabled(1);
        gateway.setCreatedTime(LocalDateTime.now().minusHours(1));
        gateway.setUpdatedTime(LocalDateTime.now().minusMinutes(10));
        return gateway;
    }

    private EapGatewayService service() {
        EapProtocolDriverRegistry registry = new EapProtocolDriverRegistry(List.of(
                new SimulatedHttpProtocolDriver(),
                new VendorHttpProtocolDriver(),
                new SecsGemProtocolDriver(),
                new OpcUaProtocolDriver()
        ));
        return new EapGatewayService(gatewayMapper, messageMapper, healthCheckMapper, auditLogService, registry);
    }
}
