package com.visionox.mes.equipment.driver;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.entity.EquipmentGatewayConnection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真机联调单元测试：用 {@link MockEquipmentSimulator} 构造与真机一致的入站协议帧，
 * 驱动真实 {@link SecsGemProtocolDriver} 和 {@link OpcUaProtocolDriver} 做帧校验、归一化和健康检查状态机断言。
 *
 * <p>对应 EAP 真机联调准备清单「验收标准」中无需真实硬件即可验证的项：
 * S/F 帧握手归一化、CEID/RPTID/SVID 解析、OPC UA 节点数据变化归一化、SHADOW/EXTERNAL 健康状态机。
 * 真实 HSMS/Session 连接握手、心跳重连属 ACTIVE 模式，需引入客户端库并在真机环境验证，不在本测试范围。
 */
class ProtocolDriverHandshakeTest {

    private final SecsGemProtocolDriver secsGemDriver = new SecsGemProtocolDriver();
    private final OpcUaProtocolDriver opcUaDriver = new OpcUaProtocolDriver();

    @Test
    @SuppressWarnings("unchecked")
    void secsGemEventReportShouldNormalizeToStatusAndPreserveCeidTrace() {
        EquipmentGatewayConnection gateway = secsGemGateway("SHADOW");
        Map<String, Object> frame = MockEquipmentSimulator.secsGemEventReportS6F11(
                "EVAP_01", "1001", "RPT-STATUS", Map.of("status", "RUNNING", "operatorId", "ee1001"));

        Map<String, Object> normalized = secsGemDriver.normalizeInbound(gateway, frame);

        assertThat(normalized.get("protocolType")).isEqualTo("SECS_GEM");
        assertThat(normalized.get("driverCode")).isEqualTo("secs-gem-driver");
        assertThat(normalized.get("messageType")).isEqualTo("STATUS");
        Map<String, Object> payload = (Map<String, Object>) normalized.get("payload");
        assertThat(payload).containsEntry("secsMessage", "S6F11");
        assertThat(payload).containsEntry("ceid", "1001");
        assertThat(payload).containsEntry("rptId", "RPT-STATUS");
        assertThat(payload).containsEntry("systemBytes", "0x0000A1B2");
        assertThat(payload).containsEntry("status", "RUNNING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void secsGemTraceDataShouldNormalizeToParameterWithSvid() {
        EquipmentGatewayConnection gateway = secsGemGateway("SHADOW");
        Map<String, Object> frame = MockEquipmentSimulator.secsGemTraceDataS6F1(
                "EVAP_01", "SVID-201", "CHAMBER_TEMP", 248.6, "C");

        Map<String, Object> normalized = secsGemDriver.normalizeInbound(gateway, frame);

        assertThat(normalized.get("messageType")).isEqualTo("PARAMETER");
        Map<String, Object> payload = (Map<String, Object>) normalized.get("payload");
        assertThat(payload).containsEntry("secsMessage", "S6F1");
        assertThat(payload).containsEntry("svid", "SVID-201");
        assertThat(payload).containsEntry("paramCode", "CHAMBER_TEMP");
        assertThat(payload).containsEntry("paramValue", 248.6);
    }

    @Test
    void secsGemRecipeSelectShouldNormalizeToRecipeDownload() {
        EquipmentGatewayConnection gateway = secsGemGateway("SHADOW");
        Map<String, Object> frame = MockEquipmentSimulator.secsGemRecipeSelectS2F41("EVAP_01", "RCP_EVAP_001");

        Map<String, Object> normalized = secsGemDriver.normalizeInbound(gateway, frame);

        assertThat(normalized.get("messageType")).isEqualTo("RECIPE_DOWNLOAD");
    }

    @Test
    void secsGemDriverShouldRejectFrameWithoutStreamFunction() {
        EquipmentGatewayConnection gateway = secsGemGateway("SHADOW");
        Map<String, Object> badFrame = MockEquipmentSimulator.secsGemFrameWithoutStreamFunction("EVAP_01");

        assertThatThrownBy(() -> secsGemDriver.normalizeInbound(gateway, badFrame))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SECS/GEM frame is invalid");
    }

    @Test
    void secsGemDriverShouldRejectFrameWithoutEquipmentIdentity() {
        EquipmentGatewayConnection gateway = secsGemGateway("SHADOW");
        Map<String, Object> badFrame = MockEquipmentSimulator.secsGemEventReportS6F11(
                "", "1001", "RPT-STATUS", Map.of("status", "RUNNING"));
        badFrame.remove("equipmentCode");
        ((Map<String, Object>) badFrame.get("payload")).remove("equipmentCode");

        assertThatThrownBy(() -> secsGemDriver.normalizeInbound(gateway, badFrame))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SECS/GEM equipment identity is missing");
    }

    @Test
    void secsGemProcessCycleSequenceShouldNormalizeEachFrameByMessageType() {
        EquipmentGatewayConnection gateway = secsGemGateway("SHADOW");
        List<Map<String, Object>> sequence = MockEquipmentSimulator.secsGemProcessCycleSequence("EVAP_01");

        List<String> messageTypes = sequence.stream()
                .map(frame -> String.valueOf(secsGemDriver.normalizeInbound(gateway, frame).get("messageType")))
                .toList();

        assertThat(messageTypes).containsExactly("STATUS", "PARAMETER", "PARAMETER", "RECIPE_DOWNLOAD");
    }

    @Test
    @SuppressWarnings("unchecked")
    void opcUaDataChangeShouldNormalizeToParameterAndPreserveNodeMetadata() {
        EquipmentGatewayConnection gateway = opcUaGateway("SHADOW");
        Map<String, Object> frame = MockEquipmentSimulator.opcUaDataChange(
                "COATER_01", "ns=2;s=Coater01.Thickness", "THICKNESS", 2.05, "um");

        Map<String, Object> normalized = opcUaDriver.normalizeInbound(gateway, frame);

        assertThat(normalized.get("protocolType")).isEqualTo("OPC_UA");
        assertThat(normalized.get("messageType")).isEqualTo("PARAMETER");
        Map<String, Object> payload = (Map<String, Object>) normalized.get("payload");
        assertThat(payload).containsEntry("nodeId", "ns=2;s=Coater01.Thickness");
        assertThat(payload).containsEntry("browseName", "THICKNESS");
        assertThat(payload).containsEntry("namespaceIndex", 2);
        assertThat(payload).containsEntry("qualityCode", "Good");
        assertThat(payload).containsEntry("sourceTimestamp", "2026-06-26T09:00:00.500Z");
        assertThat(payload).containsEntry("serverTimestamp", "2026-06-26T09:00:00.520Z");
    }

    @Test
    void opcUaDriverShouldRejectFrameWithoutNodeId() {
        EquipmentGatewayConnection gateway = opcUaGateway("SHADOW");
        Map<String, Object> badFrame = MockEquipmentSimulator.opcUaDataChangeWithoutNodeId("COATER_01");

        assertThatThrownBy(() -> opcUaDriver.normalizeInbound(gateway, badFrame))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OPC UA node identity is missing");
    }

    @Test
    void shadowGatewayHealthCheckShouldWarnUntilRealHandshakeConfigured() {
        EquipmentGatewayConnection gateway = secsGemGateway("SHADOW");

        Map<String, Object> health = secsGemDriver.checkHealth(gateway, Map.of());

        assertThat(health.get("resultStatus")).isEqualTo("WARN");
        assertThat(health.get("message")).asString().contains("real equipment handshake not configured");
        assertThat(health.get("requiresRealEquipmentLink")).isEqualTo(true);
    }

    @Test
    void externalGatewayHealthCheckShouldFailWithoutRealLinkConfig() {
        EquipmentGatewayConnection gateway = opcUaGateway("EXTERNAL");

        Map<String, Object> health = opcUaDriver.checkHealth(gateway, Map.of());

        assertThat(health.get("resultStatus")).isEqualTo("FAIL");
        assertThat(health.get("message")).asString().contains("external equipment link is not configured");
    }

    @Test
    void externalGatewayHealthCheckShouldWarnWhenRealLinkConfigured() {
        EquipmentGatewayConnection gateway = opcUaGateway("EXTERNAL");

        Map<String, Object> health = opcUaDriver.checkHealth(gateway, Map.of("realLinkConfigured", true));

        assertThat(health.get("resultStatus")).isEqualTo("WARN");
        assertThat(health.get("message")).asString().contains("live equipment handshake must be verified");
    }

    private EquipmentGatewayConnection secsGemGateway(String driverMode) {
        return gateway("GW-SECSGEM-SHADOW", "SECS_GEM", "secs-gem-driver",
                "hsms://192.168.1.100:5000", driverMode);
    }

    private EquipmentGatewayConnection opcUaGateway(String driverMode) {
        return gateway("GW-OPCUA-SHADOW", "OPC_UA", "opc-ua-driver",
                "opc.tcp://192.168.1.101:4840", driverMode);
    }

    private EquipmentGatewayConnection gateway(String gatewayCode, String protocolType, String driverCode,
                                               String endpointUri, String driverMode) {
        EquipmentGatewayConnection gateway = new EquipmentGatewayConnection();
        gateway.setGatewayCode(gatewayCode);
        gateway.setGatewayName(gatewayCode);
        gateway.setProtocolType(protocolType);
        gateway.setDriverCode(driverCode);
        gateway.setDriverMode(driverMode);
        gateway.setEndpointUri(endpointUri);
        gateway.setLineCode("LINE_01");
        gateway.setEnabled(1);
        return gateway;
    }
}
