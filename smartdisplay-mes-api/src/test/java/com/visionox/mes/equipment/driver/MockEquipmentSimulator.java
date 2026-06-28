package com.visionox.mes.equipment.driver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真机联调 Mock 设备模拟器。
 *
 * <p>在没有真实 SECS/GEM、OPC UA 设备的前提下，按 SEMI E5/E30/E37 与 OPC UA Part 4 的报文语义，
 * 构造与真机一致的入站协议帧，供协议驱动做帧校验和归一化的真机联调单元测试使用。
 *
 * <p>边界说明：本模拟器只覆盖入站帧的内容与字段结构，不模拟 HSMS/Session 连接握手、心跳和重连
 * （这些属于 ACTIVE 模式真实链路，需引入 secs4j / Eclipse Milo 客户端并在真机环境验证）。
 */
final class MockEquipmentSimulator {

    private MockEquipmentSimulator() {
    }

    /**
     * SECS/GEM S6F11 事件报告（Event Report Send）。
     *
     * <p>真机在 CEID 触发时主动上报，携带 RPTID 与该报告关联的变量值列表。
     * 驱动应把 S6F11 归一为 STATUS 消息，并保留 ceid/rptId/systemBytes 等追溯字段。
     */
    static Map<String, Object> secsGemEventReportS6F11(String equipmentCode, String ceid, String rptId,
                                                       Map<String, Object> reportVariables) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("stream", "6");
        frame.put("function", "11");
        frame.put("equipmentCode", equipmentCode);
        frame.put("deviceId", "1");
        frame.put("ceid", ceid);
        frame.put("rptId", rptId);
        frame.put("systemBytes", "0x0000A1B2");
        frame.put("transactionId", "TX-" + ceid);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("equipmentCode", equipmentCode);
        payload.put("ceid", ceid);
        payload.put("rptId", rptId);
        payload.putAll(reportVariables);
        frame.put("payload", payload);
        return frame;
    }

    /**
     * SECS/GEM S6F1 Trace Data Send（状态变量采样）。
     *
     * <p>携带 SVID 列表与采样值，驱动应归一为 PARAMETER 消息。
     */
    static Map<String, Object> secsGemTraceDataS6F1(String equipmentCode, String svid, String paramCode,
                                                    Object paramValue, String unit) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("stream", "6");
        frame.put("function", "1");
        frame.put("equipmentCode", equipmentCode);
        frame.put("deviceId", "1");
        frame.put("systemBytes", "0x0000C3D4");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("equipmentCode", equipmentCode);
        payload.put("svid", svid);
        payload.put("paramCode", paramCode);
        payload.put("paramValue", paramValue);
        payload.put("unit", unit);
        frame.put("payload", payload);
        return frame;
    }

    /**
     * SECS/GEM S2F41 Host Command Send（PP-Select 配方下发）。
     *
     * <p>驱动应归一为 RECIPE_DOWNLOAD 消息。
     */
    static Map<String, Object> secsGemRecipeSelectS2F41(String equipmentCode, String recipeCode) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("stream", "2");
        frame.put("function", "41");
        frame.put("equipmentCode", equipmentCode);
        frame.put("deviceId", "1");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("equipmentCode", equipmentCode);
        payload.put("recipeCode", recipeCode);
        payload.put("command", "PP-SELECT");
        frame.put("payload", payload);
        return frame;
    }

    /**
     * 缺失 S/F 帧标识的非法 SECS/GEM 帧，用于验证驱动拒绝坏帧。
     */
    static Map<String, Object> secsGemFrameWithoutStreamFunction(String equipmentCode) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("equipmentCode", equipmentCode);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("equipmentCode", equipmentCode);
        payload.put("status", "RUNNING");
        frame.put("payload", payload);
        return frame;
    }

    /**
     * OPC UA 订阅数据变化通知（PublishResponse / MonitoredItem DataChange）。
     *
     * <p>携带 NodeId、Value、Quality(StatusCode)、源/服务器时间戳，驱动应归一为 PARAMETER 消息。
     */
    static Map<String, Object> opcUaDataChange(String equipmentCode, String nodeId, String browseName,
                                               Object value, String unit) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("operation", "DATA_CHANGE");
        frame.put("equipmentCode", equipmentCode);
        frame.put("nodeId", nodeId);
        frame.put("browseName", browseName);
        frame.put("namespaceIndex", 2);
        frame.put("monitoredItemId", "MI-" + browseName);
        frame.put("qualityCode", "Good");
        frame.put("sourceTimestamp", "2026-06-26T09:00:00.500Z");
        frame.put("serverTimestamp", "2026-06-26T09:00:00.520Z");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("equipmentCode", equipmentCode);
        payload.put("paramCode", browseName);
        payload.put("paramValue", value);
        payload.put("unit", unit);
        frame.put("payload", payload);
        return frame;
    }

    /**
     * 缺失 NodeId 的非法 OPC UA 帧，用于验证驱动拒绝坏帧。
     */
    static Map<String, Object> opcUaDataChangeWithoutNodeId(String equipmentCode) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("operation", "DATA_CHANGE");
        frame.put("equipmentCode", equipmentCode);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("equipmentCode", equipmentCode);
        payload.put("paramValue", 2.05);
        frame.put("payload", payload);
        return frame;
    }

    /**
     * 一段连续上报序列，模拟真机在一个工艺循环内先报状态、再报多点参数、最后报配方下发回执。
     */
    static List<Map<String, Object>> secsGemProcessCycleSequence(String equipmentCode) {
        return List.of(
                secsGemEventReportS6F11(equipmentCode, "1001", "RPT-STATUS",
                        Map.of("status", "RUNNING", "operatorId", "ee1001")),
                secsGemTraceDataS6F1(equipmentCode, "SVID-201", "CHAMBER_TEMP", 248.6, "C"),
                secsGemTraceDataS6F1(equipmentCode, "SVID-202", "CHAMBER_PRESSURE", 1.2e-4, "Pa"),
                secsGemRecipeSelectS2F41(equipmentCode, "RCP_EVAP_001"));
    }
}
